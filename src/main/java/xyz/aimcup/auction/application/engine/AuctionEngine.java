package xyz.aimcup.auction.application.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.exception.BadRequestException;
import xyz.aimcup.auction.domain.exception.ConflictException;
import xyz.aimcup.auction.domain.exception.ForbiddenException;
import xyz.aimcup.auction.domain.exception.NotFoundException;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionPhase;
import xyz.aimcup.auction.domain.model.AuctionSettings;
import xyz.aimcup.auction.domain.model.AuctionStage;
import xyz.aimcup.auction.domain.model.AuctionState;
import xyz.aimcup.auction.domain.model.BidEvent;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.LiveAuctionState;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.model.PlayerStatus;
import xyz.aimcup.auction.domain.port.in.AuctionChannelLookup;
import xyz.aimcup.auction.domain.port.in.AuctionControlUseCase;
import xyz.aimcup.auction.domain.port.in.BiddingUseCase;
import xyz.aimcup.auction.domain.port.in.ReadinessUseCase;
import xyz.aimcup.auction.domain.port.in.command.BidActor;
import xyz.aimcup.auction.domain.port.in.command.BidResult;
import xyz.aimcup.auction.domain.port.in.command.ReadyResult;
import xyz.aimcup.auction.domain.port.out.AuctionRepositoryPort;
import xyz.aimcup.auction.domain.port.out.DiscordNotificationPort;
import xyz.aimcup.auction.domain.port.out.LiveStatePort;
import xyz.aimcup.auction.security.PresenceTracker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reactive auction engine. It is the single source of truth for every running auction and is the
 * one place auction state mutates.
 *
 * <h2>Concurrency</h2>
 * Every state change — a bid from the web or Discord, an organizer command, a timer firing — is
 * funneled onto one dedicated worker thread ({@code engineScheduler}). Because that thread is
 * single-threaded, mutations are naturally serialized without locks. Slow work (Mongo, Redis,
 * Discord) is dispatched asynchronously off that thread so the engine never blocks.
 *
 * <h2>Timers</h2>
 * Instead of ticking every second, the engine schedules a single deadline per phase and broadcasts a
 * snapshot carrying {@code phaseEndsAtEpochMs}; clients run the countdown locally. A new bid simply
 * cancels and reschedules that deadline.
 *
 * <h2>Persistence</h2>
 * The live snapshot is mirrored to Redis on every change; the durable MongoDB store is written when a
 * player's bidding completes (and at start/pause/resume/finish/organizer edits), satisfying the
 * "flush after each player" requirement.
 */
@Slf4j
@Component
public class AuctionEngine implements AuctionControlUseCase, BiddingUseCase, ReadinessUseCase, AuctionChannelLookup {

    private final AuctionRepositoryPort auctionRepository;
    private final LiveStatePort liveStatePort;
    private final DiscordNotificationPort discord;
    private final PresenceTracker presence;
    private final AuctionProperties properties;

    /**
     * How long the "which captain wins" reveal lasts after the max-bid window closes. Fixed (not
     * organizer-configurable): it only paces the client animation, and the client reads the exact end
     * time from {@code phaseEndsAtEpochMs}, so the two stay in lock-step.
     */
    private static final int MAX_BID_DRAW_SECONDS = 5;

    private final Scheduler engineScheduler = Schedulers.newSingle("auction-engine", true);
    private final Map<UUID, RunningAuction> running = new ConcurrentHashMap<>();
    private final Map<UUID, Sinks.Many<LiveAuctionState>> sinks = new ConcurrentHashMap<>();
    /** When the last readiness reminder was sent per auction, to honour the configured cadence. */
    private final Map<UUID, Instant> lastReminderSent = new ConcurrentHashMap<>();

    public AuctionEngine(AuctionRepositoryPort auctionRepository, LiveStatePort liveStatePort,
                         DiscordNotificationPort discord, PresenceTracker presence,
                         AuctionProperties properties) {
        this.auctionRepository = auctionRepository;
        this.liveStatePort = liveStatePort;
        this.discord = discord;
        this.presence = presence;
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        // Re-broadcast a fresh snapshot (with updated online dots) whenever presence changes.
        presence.changes()
                .filter(sinks::containsKey)
                .flatMap(id -> liveSnapshot(id).doOnNext(snapshot -> emitTo(id, snapshot)))
                .onErrorContinue((e, o) -> log.warn("presence rebroadcast failed: {}", e.toString()))
                .subscribe();

        // Ping captains who have not confirmed readiness, on a schedule anchored to each auction's
        // start time (see AuctionProperties.Reminder). The scheduler wakes every tickSeconds; whether
        // a given auction is actually due is decided per-auction in remindNotReady().
        AuctionProperties.Reminder cfg = properties.getReminder();
        if (cfg.isEnabled()) {
            Flux.interval(Duration.ofSeconds(Math.max(10, cfg.getTickSeconds())), Schedulers.boundedElastic())
                    .onBackpressureDrop()
                    // concatMap (not flatMap) so a slow pass can never overlap the next tick and
                    // double-send a reminder for the same auction.
                    .concatMap(tick -> remindNotReady())
                    .onErrorContinue((e, o) -> log.warn("readiness reminder failed: {}", e.toString()))
                    .subscribe();
        }
    }

    // ====================================================================================
    //  Crash recovery
    // ====================================================================================

    /**
     * After a restart, any auction left RUNNING or PAUSED in the durable store has lost its in-memory
     * runtime state — the player queue, stage cursor and timers live only in {@link RunningAuction},
     * never in Mongo. Without recovery such an auction is a zombie: the store says RUNNING but the
     * engine has no record of it, so bids, pause/resume and even a fresh start all fail.
     *
     * <p>On startup we rebuild that runtime state from the persisted roster (player statuses, balances
     * and the stage cursor are all durable) and force every interrupted auction into PAUSED. An
     * organizer must press resume — and captains re-confirm readiness — before bidding continues; we
     * never silently restart a countdown that was cut off mid-bid. Sold/unsold results already flushed
     * to Mongo are preserved; only the in-flight player (still AVAILABLE) returns to the queue.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedAuctions() {
        Flux.concat(auctionRepository.findByState(AuctionState.RUNNING),
                        auctionRepository.findByState(AuctionState.PAUSED))
                .flatMap(auction -> Mono.<Void>fromRunnable(() -> rehydrateAsPaused(auction))
                        .subscribeOn(engineScheduler))
                .onErrorContinue((e, o) -> log.error("Failed to recover an interrupted auction: {}", e.toString()))
                .subscribe();
    }

    /**
     * Rebuilds a {@link RunningAuction} for an auction that was interrupted by a restart and parks it
     * in PAUSED. Runs on the engine thread so it is serialized with every other state change.
     */
    private void rehydrateAsPaused(Auction auction) {
        if (running.containsKey(auction.getId())) {
            return; // already live on this instance — nothing to recover
        }
        boolean wasRunning = auction.getState() == AuctionState.RUNNING;

        RunningAuction ra = new RunningAuction(auction, sinkFor(auction.getId()));
        ra.stageIndex = auction.getCurrentStageIndex();
        // AVAILABLE auctionable players are exactly the current stage's remaining pool (including the
        // player who was up when the crash happened — they were never finalized), so this faithfully
        // restores where bidding left off.
        List<UUID> remaining = new ArrayList<>(auction.auctionablePlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.AVAILABLE)
                .map(Player::getId)
                .toList());
        // Stages after the first re-auction their pool in random order (see onStageComplete); keep that
        // property on recovery so a mid-stage restart doesn't silently fall back to a fixed ordering.
        if (ra.stageIndex > 0) {
            Collections.shuffle(remaining);
        }
        ra.queue = new ArrayDeque<>(remaining);
        ra.phase = AuctionPhase.PAUSED;
        ra.currentPlayerId = null;
        ra.highestBid = 0;
        ra.highestBidderId = null;
        ra.highestBidderUsername = null;
        ra.bidHistory = new ArrayList<>();
        ra.phaseEndsAtEpochMs = 0;
        ra.pauseRequested = false;
        ra.message = "Auction paused after a server restart — an organizer must resume it";

        if (wasRunning) {
            // A live auction was cut off: drop it to PAUSED and make captains re-confirm readiness,
            // mirroring a normal organizer pause (see enterPaused). Already-PAUSED auctions keep their
            // persisted state and readiness untouched — we only rebuild their runtime state.
            auction.setState(AuctionState.PAUSED);
            auction.getCaptains().forEach(c -> c.setReady(false));
            persistAsync(auction);
            discord.auctionPaused(auction).subscribe();
        }

        running.put(auction.getId(), ra);
        broadcast(ra);
        log.info("Recovered auction {} as PAUSED ({} players still to auction, stage {})",
                auction.getId(), ra.queue.size(), ra.stageIndex);
    }

    private Mono<Void> remindNotReady() {
        Instant now = Instant.now();
        // Reads PAUSED auctions' state/readiness off the engine thread; this is intentional and
        // tolerates slight staleness — the worst case is one reminder fired/skipped a tick early or
        // late, self-corrected on the next tick. Reminders never mutate auction state.
        List<Auction> pausedRunning = running.values().stream()
                .map(ra -> ra.auction)
                .filter(a -> a.getState() == AuctionState.PAUSED)
                .toList();
        return Flux.concat(Flux.fromIterable(pausedRunning),
                        auctionRepository.findByState(AuctionState.SCHEDULED))
                .filter(a -> a.getChannelId() != null && !a.getChannelId().isBlank())
                .filter(a -> a.getCaptains().stream().anyMatch(c -> !c.isReady()))
                .flatMap(a -> {
                    Integer intervalMinutes = dueReminderIntervalMinutes(a, now);
                    if (intervalMinutes == null) {
                        return Mono.<Void>empty();
                    }
                    Instant last = lastReminderSent.get(a.getId());
                    if (last != null
                            && Duration.between(last, now).getSeconds() < intervalMinutes * 60L - 30) {
                        return Mono.<Void>empty();
                    }
                    lastReminderSent.put(a.getId(), now);
                    List<Captain> notReady = a.getCaptains().stream().filter(c -> !c.isReady()).toList();
                    return discord.remindNotReady(a, notReady);
                })
                .then();
    }

    /**
     * The current reminder interval (minutes) for an auction, or {@code null} if no reminder is due
     * yet. SCHEDULED auctions follow the start-anchored phases; PAUSED auctions (no start anchor) use
     * the post-start cadence so captains re-confirm promptly.
     */
    private Integer dueReminderIntervalMinutes(Auction a, Instant now) {
        AuctionProperties.Reminder cfg = properties.getReminder();
        if (a.getState() == AuctionState.PAUSED) {
            return cfg.getAfterStartIntervalMinutes();
        }
        Instant startAt = a.getStartAt();
        if (startAt == null) {
            return null; // no start time → nothing to anchor reminders to
        }
        Instant windowStart = startAt.minus(Duration.ofMinutes(cfg.getLeadTimeMinutes()));
        if (now.isBefore(windowStart)) {
            return null; // too early — more than leadTime before the start
        }
        if (now.isBefore(startAt)) {
            Instant phase1End = windowStart.plus(Duration.ofMinutes(cfg.getPhase1DurationMinutes()));
            return now.isBefore(phase1End) ? cfg.getPhase1IntervalMinutes() : cfg.getPhase2IntervalMinutes();
        }
        return cfg.getAfterStartIntervalMinutes(); // start time passed, still not running
    }

    // ====================================================================================
    //  Live streaming (consumed by the query service / subscription resolver)
    // ====================================================================================

    /** Hot stream: current snapshot first, then every change. */
    public Flux<LiveAuctionState> liveStream(UUID auctionId) {
        Sinks.Many<LiveAuctionState> sink = sinkFor(auctionId);
        return Flux.concat(liveSnapshot(auctionId), sink.asFlux());
    }

    /** Current snapshot, from the running engine when active, otherwise derived from the store. */
    public Mono<LiveAuctionState> liveSnapshot(UUID auctionId) {
        RunningAuction ra = running.get(auctionId);
        if (ra != null) {
            return Mono.fromCallable(() -> buildSnapshot(ra)).subscribeOn(engineScheduler);
        }
        return auctionRepository.findById(auctionId).map(this::buildSnapshotFromStore);
    }

    private Sinks.Many<LiveAuctionState> sinkFor(UUID auctionId) {
        return sinks.computeIfAbsent(auctionId, id -> Sinks.many().multicast().directBestEffort());
    }

    private void emitTo(UUID auctionId, LiveAuctionState snapshot) {
        Sinks.Many<LiveAuctionState> sink = sinks.get(auctionId);
        if (sink != null) {
            sink.tryEmitNext(snapshot);
        }
    }

    @Override
    public Mono<Auction> findActiveByChannelId(String channelId) {
        if (channelId == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> running.values().stream()
                        .map(ra -> ra.auction)
                        .filter(a -> channelId.equals(a.getChannelId()))
                        .findFirst()
                        .orElse(null))
                .subscribeOn(engineScheduler);
    }

    // ====================================================================================
    //  Control use case
    // ====================================================================================

    @Override
    public Mono<Void> start(long actingOsuId, UUID auctionId) {
        return auctionRepository.findById(auctionId)
                .switchIfEmpty(Mono.error(new NotFoundException("Auction not found")))
                .flatMap(auction -> Mono.<Void>fromRunnable(() -> doStart(auction, actingOsuId))
                        .subscribeOn(engineScheduler))
                .then();
    }

    private void doStart(Auction auction, long actingOsuId) {
        requireManager(auction, actingOsuId);
        if (running.containsKey(auction.getId()) || auction.getState() == AuctionState.RUNNING) {
            throw new ConflictException("Auction is already running");
        }
        if (auction.getState() == AuctionState.FINISHED) {
            throw new ConflictException("Auction has already finished");
        }
        if (auction.getCaptains().isEmpty()) {
            throw new BadRequestException("Select at least one captain before starting");
        }
        if (auction.getStages().isEmpty()) {
            throw new BadRequestException("Configure at least one stage before starting");
        }

        AuctionSettings settings = auction.getSettings();
        for (Captain captain : auction.getCaptains()) {
            captain.setBalance(settings.getStartingBalance());
            captain.setTeamPlayerIds(new ArrayList<>());
        }
        for (Player player : auction.auctionablePlayers()) {
            player.setStatus(PlayerStatus.AVAILABLE);
            player.setSoldToCaptainId(null);
            player.setSoldPrice(null);
        }

        auction.setState(AuctionState.RUNNING);
        auction.setStartedAt(Instant.now());
        auction.setCurrentStageIndex(0);

        RunningAuction ra = new RunningAuction(auction, sinkFor(auction.getId()));
        ra.stageIndex = 0;
        ra.queue = new ArrayDeque<>(auction.auctionablePlayers().stream().map(Player::getId).toList());
        running.put(auction.getId(), ra);
        lastReminderSent.remove(auction.getId());

        persistAsync(auction);
        discord.auctionStarted(auction).subscribe();
        log.info("Auction {} started with {} captains and {} players",
                auction.getId(), auction.getCaptains().size(), ra.queue.size());

        nextPlayer(ra);
    }

    @Override
    public Mono<Void> pause(long actingOsuId, UUID auctionId) {
        return onEngine(() -> {
            RunningAuction ra = requireRunning(auctionId);
            requireManager(ra.auction, actingOsuId);
            if (ra.auction.getState() != AuctionState.RUNNING) {
                throw new ConflictException("Auction is not running");
            }
            ra.pauseRequested = true;
            ra.message = "Pause requested — the current player will finish first";
            broadcast(ra);
        });
    }

    @Override
    public Mono<Void> resume(long actingOsuId, UUID auctionId) {
        return onEngine(() -> {
            RunningAuction ra = requireRunning(auctionId);
            requireManager(ra.auction, actingOsuId);
            if (ra.auction.getState() != AuctionState.PAUSED) {
                throw new ConflictException("Auction is not paused");
            }
            ra.auction.setState(AuctionState.RUNNING);
            ra.pauseRequested = false;
            ra.message = null;
            persistAsync(ra.auction);
            discord.auctionResumed(ra.auction).subscribe();
            // The gap before the next player is doubled once on resume.
            enterGap(ra, true);
        });
    }

    @Override
    public Mono<Void> removePlayerFromTeam(long actingOsuId, UUID auctionId, UUID playerId) {
        return onEngine(() -> {
            RunningAuction ra = requireRunning(auctionId);
            requireManager(ra.auction, actingOsuId);
            if (ra.auction.getState() != AuctionState.PAUSED) {
                throw new ConflictException("Players can only be removed while the auction is paused");
            }
            Player player = ra.auction.findPlayer(playerId)
                    .orElseThrow(() -> new NotFoundException("Player not found"));
            if (player.getStatus() != PlayerStatus.SOLD || player.getSoldToCaptainId() == null) {
                throw new BadRequestException("Player is not currently on a team");
            }
            Captain captain = ra.auction.findCaptain(player.getSoldToCaptainId())
                    .orElseThrow(() -> new NotFoundException("Captain not found"));
            captain.setBalance(captain.getBalance() + (player.getSoldPrice() == null ? 0 : player.getSoldPrice()));
            captain.getTeamPlayerIds().remove(playerId);
            player.setStatus(PlayerStatus.AVAILABLE);
            player.setSoldToCaptainId(null);
            player.setSoldPrice(null);
            // Return the player to the current stage's pool so they can be re-auctioned right away
            // (rather than waiting for the next stage). Skip if somehow already queued.
            if (!ra.queue.contains(playerId)) {
                ra.queue.addLast(playerId);
            }
            persistAsync(ra.auction);
            broadcast(ra);
        });
    }

    @Override
    public Mono<Void> changeCaptainBalance(long actingOsuId, UUID auctionId, UUID captainId, int newBalance) {
        return onEngine(() -> {
            RunningAuction ra = requireRunning(auctionId);
            requireManager(ra.auction, actingOsuId);
            Captain captain = ra.auction.findCaptain(captainId)
                    .orElseThrow(() -> new NotFoundException("Captain not found"));
            captain.setBalance(Math.max(0, newBalance));
            persistAsync(ra.auction);
            broadcast(ra);
        });
    }

    // ====================================================================================
    //  Bidding use case
    // ====================================================================================

    @Override
    public Mono<BidResult> placeBid(UUID auctionId, BidActor actor, int amount, String source) {
        return Mono.fromCallable(() -> doBid(auctionId, actor, amount, false, source)).subscribeOn(engineScheduler);
    }

    @Override
    public Mono<BidResult> placeMaxBid(UUID auctionId, BidActor actor, String source) {
        return Mono.fromCallable(() -> doBid(auctionId, actor, 0, true, source)).subscribeOn(engineScheduler);
    }

    private BidResult doBid(UUID auctionId, BidActor actor, int requested, boolean forceMax, String source) {
        RunningAuction ra = running.get(auctionId);
        // Bids are accepted while a player is up: normal bids during BIDDING, counter max bids during
        // the MAX_BID_WINDOW. The MAX_BID_DRAW (winner already chosen) and every other phase are closed.
        boolean biddable = ra != null && ra.currentPlayerId != null
                && (ra.phase == AuctionPhase.BIDDING || ra.phase == AuctionPhase.MAX_BID_WINDOW);
        if (!biddable) {
            return BidResult.rejected("No player is currently up for bidding", ra == null ? 0 : ra.highestBid);
        }
        Captain captain = resolveCaptain(ra, actor);
        if (captain == null) {
            return BidResult.rejected("Only captains of this auction may bid", ra.highestBid);
        }
        AuctionSettings s = ra.auction.getSettings();
        if (s.getMaxTeamSize() > 0 && captain.teamSize() >= s.getMaxTeamSize()) {
            return BidResult.rejected("Your team is full (" + s.getMaxTeamSize() + " players)", ra.highestBid);
        }
        int bid = forceMax ? s.getMaxBid() : requested;
        boolean isMax = bid == s.getMaxBid();

        // Once a max bid is called the price is locked at the max; only counter max bids matter.
        if (ra.phase == AuctionPhase.MAX_BID_WINDOW && !isMax) {
            return BidResult.rejected("A max bid was called — you can only counter with your own max bid",
                    ra.highestBid);
        }

        if (bid <= 0) {
            return BidResult.rejected("Bid must be a positive amount", ra.highestBid);
        }
        if (bid > s.getMaxBid()) {
            return BidResult.rejected("The maximum bid is " + s.getMaxBid(), ra.highestBid);
        }
        if (bid > captain.getBalance()) {
            return BidResult.rejected("You only have " + captain.getBalance() + " credits", ra.highestBid);
        }
        if (captain.teamSize() < s.getTeamSizeForPercentLimit()) {
            int cap = (int) Math.floor(captain.getBalance() * (s.getMaxBidPercent() / 100.0));
            if (bid > cap) {
                return BidResult.rejected("Until you have " + s.getTeamSizeForPercentLimit()
                        + " players you may bid at most " + s.getMaxBidPercent() + "% of your balance (" + cap + ")",
                        ra.highestBid);
            }
        }

        if (isMax) {
            return doMaxBid(ra, captain, s, source);
        }

        // ----- regular incremental bid (BIDDING phase only) -----
        int minNext = ra.highestBid == 0 ? s.getMinIncrement() : ra.highestBid + s.getMinIncrement();
        if (bid < minNext) {
            return BidResult.rejected("The next bid must be at least " + minNext, ra.highestBid);
        }
        if (s.getMinIncrement() > 0 && bid % s.getMinIncrement() != 0) {
            return BidResult.rejected("Bids must be in increments of " + s.getMinIncrement(), ra.highestBid);
        }

        ra.highestBid = bid;
        ra.highestBidderId = captain.getId();
        ra.highestBidderUsername = captain.getUsername();
        recordBid(ra, captain, bid, false, source);
        scheduleDeadline(ra, stage(ra).getBiddingTimeAfterBidSeconds(), () -> onBiddingDeadline(ra));
        broadcast(ra);
        return BidResult.accepted(ra.highestBid);
    }

    /**
     * Handles a max bid. The first max bid for a player opens the {@link AuctionPhase#MAX_BID_WINDOW};
     * subsequent ones join the draw pool without extending the (fixed) window. A captain may only max
     * once per player. The winner is drawn at random when the window closes — see
     * {@link #onMaxBidWindowDeadline}.
     */
    private BidResult doMaxBid(RunningAuction ra, Captain captain, AuctionSettings s, String source) {
        if (ra.maxBidders.contains(captain.getId())) {
            return BidResult.rejected("You have already placed a max bid for this player", ra.highestBid);
        }
        boolean firstMax = ra.phase != AuctionPhase.MAX_BID_WINDOW;
        ra.maxBidders.add(captain.getId());
        ra.highestBid = s.getMaxBid();
        ra.highestBidderId = captain.getId();
        ra.highestBidderUsername = captain.getUsername();
        recordBid(ra, captain, s.getMaxBid(), true, source);

        if (firstMax) {
            enterMaxBidWindow(ra);
            return BidResult.accepted("Max bid! Other captains can still counter before the draw.", ra.highestBid);
        }
        // Fixed window: a counter max bid joins the pool but never resets the clock.
        broadcast(ra);
        return BidResult.accepted("You're in the max-bid draw.", ra.highestBid);
    }

    /** Builds the bid event, appends it to the current player's history, and mirrors it to Discord. */
    private void recordBid(RunningAuction ra, Captain captain, int amount, boolean isMax, String source) {
        BidEvent event = BidEvent.builder()
                .captainId(captain.getId())
                .captainUsername(captain.getUsername())
                .captainAvatarUrl(captain.getAvatarUrl())
                .amount(amount)
                .at(Instant.now())
                .maxBid(isMax)
                .source(source)
                .build();
        ra.bidHistory.add(event);
        Player player = ra.auction.findPlayer(ra.currentPlayerId).orElse(null);
        if (player != null) {
            discord.bidPlaced(ra.auction, player, event).subscribe();
        }
    }

    private void enterMaxBidWindow(RunningAuction ra) {
        ra.phase = AuctionPhase.MAX_BID_WINDOW;
        ra.maxBidWinnerId = null;
        ra.message = "Max bid! Other captains can counter with their own max bid before the draw.";
        int seconds = Math.max(1, ra.auction.getSettings().getMaxBidWindowSeconds());
        scheduleDeadline(ra, seconds, () -> onMaxBidWindowDeadline(ra));
        broadcast(ra);
    }

    private void onMaxBidWindowDeadline(RunningAuction ra) {
        List<UUID> pool = new ArrayList<>(ra.maxBidders);
        if (pool.isEmpty()) {
            // The window only opens on a max bid, so the pool is never empty here — guard regardless.
            finalizePlayer(ra);
            return;
        }
        UUID winner = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        ra.maxBidWinnerId = winner;
        ra.highestBidderId = winner;
        ra.auction.findCaptain(winner).ifPresent(c -> ra.highestBidderUsername = c.getUsername());
        enterMaxBidDraw(ra);
    }

    private void enterMaxBidDraw(RunningAuction ra) {
        ra.phase = AuctionPhase.MAX_BID_DRAW;
        ra.message = ra.maxBidders.size() > 1
                ? "Drawing the winner from " + ra.maxBidders.size() + " max bids…"
                : "Max bid confirmed — awarding the player…";
        // The player is awarded only when this reveal window ends, so the team panels never spoil the
        // outcome before the animation lands.
        scheduleDeadline(ra, MAX_BID_DRAW_SECONDS, () -> finalizePlayer(ra));
        broadcast(ra);
    }

    private Captain resolveCaptain(RunningAuction ra, BidActor actor) {
        return resolveCaptainInAuction(ra.auction, actor);
    }

    private Captain resolveCaptainInAuction(Auction auction, BidActor actor) {
        if (actor.osuId() != null) {
            return auction.getCaptains().stream()
                    .filter(c -> c.getOsuId() != null && c.getOsuId().equals(actor.osuId()))
                    .findFirst().orElse(null);
        }
        if (actor.discordId() != null) {
            return auction.findCaptainByDiscordId(actor.discordId()).orElse(null);
        }
        return null;
    }

    // ====================================================================================
    //  Readiness use case
    // ====================================================================================

    @Override
    public Mono<ReadyResult> setReady(UUID auctionId, BidActor actor, boolean ready) {
        return Mono.defer(() -> {
            RunningAuction ra = running.get(auctionId);
            if (ra != null) {
                return Mono.fromCallable(() -> doSetReadyRunning(ra, actor, ready)).subscribeOn(engineScheduler);
            }
            return auctionRepository.findById(auctionId)
                    .switchIfEmpty(Mono.error(new NotFoundException("Auction not found")))
                    .flatMap(auction -> {
                        Captain captain = resolveCaptainInAuction(auction, actor);
                        if (captain == null) {
                            return Mono.error(new ForbiddenException("Only captains of this auction may confirm readiness"));
                        }
                        if (auction.getState() == AuctionState.FINISHED) {
                            return Mono.error(new ConflictException("The auction has already finished"));
                        }
                        captain.setReady(ready);
                        ReadyResult result = readyResult(auction, ready);
                        return auctionRepository.save(auction)
                                .doOnSuccess(saved -> {
                                    emitTo(auctionId, buildSnapshotFromStore(auction));
                                    discord.captainReadyChanged(auction, captain, ready,
                                            result.readyCount(), result.totalCaptains()).subscribe();
                                })
                                .thenReturn(result);
                    });
        });
    }

    private ReadyResult doSetReadyRunning(RunningAuction ra, BidActor actor, boolean ready) {
        Captain captain = resolveCaptainInAuction(ra.auction, actor);
        if (captain == null) {
            throw new ForbiddenException("Only captains of this auction may confirm readiness");
        }
        captain.setReady(ready);
        ReadyResult result = readyResult(ra.auction, ready);
        persistAsync(ra.auction);
        discord.captainReadyChanged(ra.auction, captain, ready, result.readyCount(), result.totalCaptains()).subscribe();
        broadcast(ra);
        return result;
    }

    private ReadyResult readyResult(Auction auction, boolean ready) {
        int total = auction.getCaptains().size();
        int readyCount = (int) auction.getCaptains().stream().filter(Captain::isReady).count();
        return new ReadyResult(ready, readyCount, total);
    }

    // ====================================================================================
    //  Auction flow (engine thread only)
    // ====================================================================================

    private void nextPlayer(RunningAuction ra) {
        if (ra.pauseRequested) {
            enterPaused(ra);
            return;
        }
        UUID nextId = ra.queue.poll();
        if (nextId == null) {
            onStageComplete(ra);
            return;
        }
        Player player = ra.auction.findPlayer(nextId).orElse(null);
        if (player == null || player.isCaptain()) {
            nextPlayer(ra);
            return;
        }
        enterBidding(ra, player);
    }

    private void enterBidding(RunningAuction ra, Player player) {
        ra.currentPlayerId = player.getId();
        ra.highestBid = 0;
        ra.highestBidderId = null;
        ra.highestBidderUsername = null;
        ra.bidHistory = new ArrayList<>();
        ra.maxBidders.clear();
        ra.maxBidWinnerId = null;
        ra.phase = AuctionPhase.BIDDING;
        ra.message = null;
        scheduleDeadline(ra, stage(ra).getBiddingTimeSeconds(), () -> onBiddingDeadline(ra));
        broadcast(ra);
        discord.playerUp(ra.auction, player, ra.stageIndex).subscribe();
    }

    private void onBiddingDeadline(RunningAuction ra) {
        finalizePlayer(ra);
    }

    private void finalizePlayer(RunningAuction ra) {
        Player player = ra.auction.findPlayer(ra.currentPlayerId).orElse(null);
        if (player == null) {
            afterPlayer(ra);
            return;
        }
        if (ra.highestBidderId != null) {
            Captain winner = ra.auction.findCaptain(ra.highestBidderId).orElse(null);
            if (winner != null) {
                winner.setBalance(winner.getBalance() - ra.highestBid);
                winner.getTeamPlayerIds().add(player.getId());
                player.setStatus(PlayerStatus.SOLD);
                player.setSoldToCaptainId(winner.getId());
                player.setSoldPrice(ra.highestBid);
                discord.playerSold(ra.auction, player, winner, ra.highestBid).subscribe();
                log.info("Player {} sold to {} for {}", player.getUsername(), winner.getUsername(), ra.highestBid);
            }
        } else {
            player.setStatus(PlayerStatus.UNSOLD);
            discord.playerUnsold(ra.auction, player).subscribe();
        }
        // Durable flush after every completed player.
        persistAsync(ra.auction);
        afterPlayer(ra);
    }

    private void afterPlayer(RunningAuction ra) {
        // The player has resolved — clear the max-bid draw state so the GAP/PAUSED snapshot is clean.
        ra.maxBidders.clear();
        ra.maxBidWinnerId = null;
        if (ra.pauseRequested) {
            enterPaused(ra);
        } else {
            enterGap(ra, false);
        }
    }

    private void enterGap(RunningAuction ra, boolean doubled) {
        ra.phase = AuctionPhase.GAP;
        ra.message = doubled ? "Resuming…" : null;
        int base = stage(ra).getGapTimeSeconds();
        int seconds = Math.max(3, doubled ? base * 2 : base);
        scheduleDeadline(ra, seconds, () -> onGapDeadline(ra));
        broadcast(ra);
    }

    private void onGapDeadline(RunningAuction ra) {
        ra.currentPlayerId = null;
        ra.highestBid = 0;
        ra.highestBidderId = null;
        ra.highestBidderUsername = null;
        ra.bidHistory = new ArrayList<>();
        nextPlayer(ra);
    }

    private void onStageComplete(RunningAuction ra) {
        int nextStage = ra.stageIndex + 1;
        List<Player> unsold = ra.auction.auctionablePlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.UNSOLD)
                .toList();
        if (nextStage < ra.auction.getStages().size() && !unsold.isEmpty()) {
            ra.stageIndex = nextStage;
            ra.auction.setCurrentStageIndex(nextStage);
            List<UUID> ids = new ArrayList<>();
            for (Player p : unsold) {
                p.setStatus(PlayerStatus.AVAILABLE);
                ids.add(p.getId());
            }
            Collections.shuffle(ids);
            ra.queue = new ArrayDeque<>(ids);
            persistAsync(ra.auction);
            log.info("Auction {} advancing to stage {} with {} unsold players",
                    ra.auction.getId(), nextStage + 1, ids.size());
            enterGap(ra, false);
        } else {
            finishAuction(ra);
        }
    }

    private void enterPaused(RunningAuction ra) {
        ra.auction.setState(AuctionState.PAUSED);
        ra.phase = AuctionPhase.PAUSED;
        ra.pauseRequested = false;
        // Captains must re-confirm readiness before the auction resumes.
        ra.auction.getCaptains().forEach(c -> c.setReady(false));
        ra.currentPlayerId = null;
        ra.highestBid = 0;
        ra.highestBidderId = null;
        ra.maxBidders.clear();
        ra.maxBidWinnerId = null;
        ra.phaseEndsAtEpochMs = 0;
        ra.message = "Auction paused by an organizer — it will resume shortly";
        cancelDeadline(ra);
        persistAsync(ra.auction);
        discord.auctionPaused(ra.auction).subscribe();
        broadcast(ra);
    }

    private void finishAuction(RunningAuction ra) {
        ra.auction.setState(AuctionState.FINISHED);
        ra.auction.setFinishedAt(Instant.now());
        ra.phase = AuctionPhase.FINISHED;
        ra.currentPlayerId = null;
        ra.maxBidders.clear();
        ra.maxBidWinnerId = null;
        ra.phaseEndsAtEpochMs = 0;
        ra.message = null;
        cancelDeadline(ra);
        persistAsync(ra.auction);
        discord.auctionFinished(ra.auction).subscribe();
        broadcast(ra);
        running.remove(ra.auction.getId());
        log.info("Auction {} finished", ra.auction.getId());
    }

    // ====================================================================================
    //  Timer / broadcast helpers
    // ====================================================================================

    private AuctionStage stage(RunningAuction ra) {
        List<AuctionStage> stages = ra.auction.getStages();
        int idx = Math.min(ra.stageIndex, stages.size() - 1);
        return stages.get(idx);
    }

    private void scheduleDeadline(RunningAuction ra, int seconds, Runnable action) {
        cancelDeadline(ra);
        ra.phaseEndsAtEpochMs = System.currentTimeMillis() + seconds * 1000L;
        ra.deadline = Mono.delay(Duration.ofSeconds(seconds), engineScheduler)
                .subscribe(tick -> safely(action), e -> log.error("deadline error", e));
    }

    private void cancelDeadline(RunningAuction ra) {
        if (ra.deadline != null && !ra.deadline.isDisposed()) {
            ra.deadline.dispose();
        }
        ra.deadline = null;
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Engine action failed", e);
        }
    }

    private void broadcast(RunningAuction ra) {
        ra.version++;
        LiveAuctionState snapshot = buildSnapshot(ra);
        ra.sink.tryEmitNext(snapshot);
        liveStatePort.save(snapshot).subscribe();
    }

    private void persistAsync(Auction auction) {
        auctionRepository.save(auction)
                .doOnError(e -> log.error("Failed to persist auction {}", auction.getId(), e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> onEngine(Runnable action) {
        return Mono.<Void>fromRunnable(action).subscribeOn(engineScheduler).then();
    }

    private RunningAuction requireRunning(UUID auctionId) {
        RunningAuction ra = running.get(auctionId);
        if (ra == null) {
            throw new ConflictException("Auction is not active on this server");
        }
        return ra;
    }

    private void requireManager(Auction auction, long osuId) {
        if (!auction.isManager(osuId) && !auction.isOwner(osuId)) {
            throw new ForbiddenException("You are not a manager of this auction");
        }
    }

    // ====================================================================================
    //  Snapshot building
    // ====================================================================================

    private LiveAuctionState buildSnapshot(RunningAuction ra) {
        Auction a = ra.auction;
        Player current = ra.currentPlayerId == null ? null
                : a.findPlayer(ra.currentPlayerId).map(this::copy).orElse(null);
        return LiveAuctionState.builder()
                .auctionId(a.getId())
                .state(a.getState())
                .phase(ra.phase)
                .stageIndex(ra.stageIndex)
                .totalStages(a.getStages().size())
                .currentPlayer(current)
                .highestBid(ra.highestBid)
                .highestBidderId(ra.highestBidderId)
                .highestBidderUsername(ra.highestBidderUsername)
                .maxBidderIds(new ArrayList<>(ra.maxBidders))
                .maxBidWinnerId(ra.maxBidWinnerId)
                .bidHistory(new ArrayList<>(ra.bidHistory))
                .phaseEndsAtEpochMs(ra.phaseEndsAtEpochMs)
                .pausedByOrganizer(a.getState() == AuctionState.PAUSED)
                .message(ra.message)
                .captains(a.getCaptains().stream().map(this::copy).toList())
                .players(a.getPlayers().stream().map(this::copy).toList())
                .onlineOsuIds(new ArrayList<>(presence.onlineOsuIds(a.getId())))
                .remainingCount(ra.queue.size() + (hasCurrentPlayerUp(ra) ? 1 : 0))
                .soldCount(countStatus(a, PlayerStatus.SOLD))
                .unsoldCount(countStatus(a, PlayerStatus.UNSOLD))
                .version(ra.version)
                .build();
    }

    private LiveAuctionState buildSnapshotFromStore(Auction a) {
        AuctionPhase phase = switch (a.getState()) {
            case SCHEDULED, RUNNING -> AuctionPhase.WAITING_TO_START;
            case PAUSED -> AuctionPhase.PAUSED;
            case FINISHED -> AuctionPhase.FINISHED;
        };
        return LiveAuctionState.builder()
                .auctionId(a.getId())
                .state(a.getState())
                .phase(phase)
                .stageIndex(a.getCurrentStageIndex())
                .totalStages(a.getStages().size())
                .currentPlayer(null)
                .highestBid(0)
                .bidHistory(new ArrayList<>())
                .phaseEndsAtEpochMs(0)
                .pausedByOrganizer(a.getState() == AuctionState.PAUSED)
                .captains(a.getCaptains().stream().map(this::copy).toList())
                .players(a.getPlayers().stream().map(this::copy).toList())
                .onlineOsuIds(new ArrayList<>(presence.onlineOsuIds(a.getId())))
                .remainingCount((int) a.auctionablePlayers().stream()
                        .filter(p -> p.getStatus() == PlayerStatus.AVAILABLE).count())
                .soldCount(countStatus(a, PlayerStatus.SOLD))
                .unsoldCount(countStatus(a, PlayerStatus.UNSOLD))
                .version(0)
                .build();
    }

    /** Whether a player is currently up and not yet awarded: bidding, in the max window, or being drawn. */
    private boolean hasCurrentPlayerUp(RunningAuction ra) {
        return ra.currentPlayerId != null
                && (ra.phase == AuctionPhase.BIDDING
                    || ra.phase == AuctionPhase.MAX_BID_WINDOW
                    || ra.phase == AuctionPhase.MAX_BID_DRAW);
    }

    private int countStatus(Auction a, PlayerStatus status) {
        return (int) a.getPlayers().stream()
                .filter(p -> !p.isCaptain() && p.getStatus() == status)
                .count();
    }

    private Player copy(Player p) {
        return p.toBuilder().build();
    }

    private Captain copy(Captain c) {
        return c.toBuilder().teamPlayerIds(new ArrayList<>(c.getTeamPlayerIds())).build();
    }
}
