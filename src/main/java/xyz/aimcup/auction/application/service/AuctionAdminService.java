package xyz.aimcup.auction.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.exception.BadRequestException;
import xyz.aimcup.auction.domain.exception.ConflictException;
import xyz.aimcup.auction.domain.exception.ForbiddenException;
import xyz.aimcup.auction.domain.exception.NotFoundException;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionSettings;
import xyz.aimcup.auction.domain.model.AuctionStage;
import xyz.aimcup.auction.domain.model.AuctionState;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.Manager;
import xyz.aimcup.auction.domain.model.OsuBeatmap;
import xyz.aimcup.auction.domain.model.OsuUserProfile;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.model.PlayerStatus;
import xyz.aimcup.auction.domain.port.in.AuctionAdminUseCase;
import xyz.aimcup.auction.domain.port.in.command.AddPlayerCommand;
import xyz.aimcup.auction.domain.port.in.command.CreateAuctionCommand;
import xyz.aimcup.auction.domain.port.in.command.ImportPlayerRow;
import xyz.aimcup.auction.domain.port.in.command.ImportPlayersResult;
import xyz.aimcup.auction.domain.port.in.command.UpdateMetaCommand;
import xyz.aimcup.auction.domain.port.out.AuctionRepositoryPort;
import xyz.aimcup.auction.domain.port.out.OsuApiPort;
import xyz.aimcup.auction.domain.port.out.UserRepositoryPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Pre-auction configuration: creating auctions, adding managers/players, CSV import and flagging
 * captains. Every method loads the aggregate, authorizes the acting user and persists the result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionAdminService implements AuctionAdminUseCase {

    private final AuctionRepositoryPort auctionRepository;
    private final UserRepositoryPort userRepository;
    private final OsuApiPort osuApi;

    @Override
    public Mono<Auction> createAuction(long actingOsuId, boolean isAdmin, CreateAuctionCommand command) {
        if (!isAdmin) {
            return Mono.error(new ForbiddenException("Only administrators can create auctions"));
        }
        if (command.name() == null || command.name().isBlank()) {
            return Mono.error(new BadRequestException("Auction name is required"));
        }
        return ownerManager(actingOsuId).map(owner -> {
            Auction auction = Auction.builder()
                    .id(UUID.randomUUID())
                    .name(command.name().trim())
                    .creatorOsuId(actingOsuId)
                    .state(AuctionState.SCHEDULED)
                    .startAt(command.startAt())
                    .createdAt(Instant.now())
                    .settings(AuctionSettings.defaults())
                    .stages(new ArrayList<>(List.of(AuctionStage.builder().index(0).build())))
                    .managers(new ArrayList<>(List.of(owner)))
                    .players(new ArrayList<>())
                    .captains(new ArrayList<>())
                    .build();
            return auction;
        }).flatMap(auctionRepository::save)
                .doOnNext(a -> log.info("Created auction {} ({}) for osu! {}", a.getId(), a.getName(), actingOsuId));
    }

    private Mono<Manager> ownerManager(long osuId) {
        return userRepository.findByOsuId(osuId)
                .map(user -> Manager.builder()
                        .id(UUID.randomUUID()).osuId(user.getOsuId()).username(user.getUsername())
                        .avatarUrl(user.getAvatarUrl()).countryCode(user.getCountryCode())
                        .owner(true).addedAt(Instant.now()).build())
                .switchIfEmpty(osuApi.fetchUser(osuId).map(p -> Manager.builder()
                        .id(UUID.randomUUID()).osuId(p.osuId()).username(p.username())
                        .avatarUrl(p.avatarUrl()).countryCode(p.countryCode())
                        .owner(true).addedAt(Instant.now()).build()));
    }

    // ---- settings / stages / meta / delete ------------------------------------------------

    @Override
    public Mono<Auction> updateSettings(long actingOsuId, UUID auctionId, AuctionSettings settings) {
        return editable(auctionId, actingOsuId).map(auction -> {
            validateSettings(settings);
            auction.setSettings(settings);
            return auction;
        }).flatMap(auctionRepository::save);
    }

    private void validateSettings(AuctionSettings s) {
        if (s.getStartingBalance() <= 0) {
            throw new BadRequestException("Starting balance must be positive");
        }
        if (s.getMaxBid() <= 0 || s.getMaxBid() > s.getStartingBalance()) {
            throw new BadRequestException("Max bid must be positive and not exceed the starting balance");
        }
        if (s.getMinIncrement() <= 0) {
            throw new BadRequestException("Minimum increment must be positive");
        }
        if (s.getMaxBidPercent() < 1 || s.getMaxBidPercent() > 100) {
            throw new BadRequestException("Max bid percent must be between 1 and 100");
        }
        if (s.getTeamSizeForPercentLimit() < 0) {
            throw new BadRequestException("Team size threshold cannot be negative");
        }
        if (s.getMaxDescriptionLength() < 1) {
            throw new BadRequestException("Description length must be positive");
        }
        if (s.getMaxTeamSize() < 1 || s.getMaxTeamSize() < s.getTeamSizeForPercentLimit()) {
            throw new BadRequestException("Maximum team size must be at least "
                    + Math.max(1, s.getTeamSizeForPercentLimit()));
        }
    }

    @Override
    public Mono<Auction> updateStages(long actingOsuId, UUID auctionId, List<AuctionStage> stages) {
        return editable(auctionId, actingOsuId).map(auction -> {
            if (stages == null || stages.isEmpty()) {
                throw new BadRequestException("At least one stage is required");
            }
            List<AuctionStage> normalized = new ArrayList<>();
            for (int i = 0; i < stages.size(); i++) {
                AuctionStage stage = stages.get(i);
                if (stage.getBiddingTimeSeconds() <= 0 || stage.getBiddingTimeAfterBidSeconds() <= 0) {
                    throw new BadRequestException("Bidding times must be positive");
                }
                if (stage.getGapTimeSeconds() < 3) {
                    throw new BadRequestException("The gap between players must be at least 3 seconds");
                }
                stage.setIndex(i);
                normalized.add(stage);
            }
            auction.setStages(normalized);
            return auction;
        }).flatMap(auctionRepository::save);
    }

    @Override
    public Mono<Auction> updateMeta(long actingOsuId, UUID auctionId, UpdateMetaCommand command) {
        return loadManaged(auctionId, actingOsuId).map(auction -> {
            if (command.banner() != null) {
                auction.setBanner(blankToNull(command.banner()));
            }
            if (command.guildId() != null) {
                auction.setGuildId(blankToNull(command.guildId()));
            }
            if (command.channelId() != null) {
                auction.setChannelId(blankToNull(command.channelId()));
            }
            // Name/start can only change before the auction begins.
            if (command.name() != null || command.startAt() != null) {
                requireScheduled(auction);
                if (command.name() != null && !command.name().isBlank()) {
                    auction.setName(command.name().trim());
                }
                if (command.startAt() != null) {
                    auction.setStartAt(command.startAt());
                }
            }
            return auction;
        }).flatMap(auctionRepository::save);
    }

    @Override
    public Mono<Void> deleteAuction(long actingOsuId, UUID auctionId) {
        return load(auctionId).flatMap(auction -> {
            if (!auction.isOwner(actingOsuId)) {
                return Mono.error(new ForbiddenException("Only the auction owner can delete it"));
            }
            if (auction.getState() == AuctionState.RUNNING || auction.getState() == AuctionState.PAUSED) {
                return Mono.error(new ConflictException("Stop the auction before deleting it"));
            }
            return auctionRepository.deleteById(auctionId);
        });
    }

    // ---- managers --------------------------------------------------------------------------

    @Override
    public Mono<Manager> addManager(long actingOsuId, UUID auctionId, long osuId, String discordId) {
        return loadManaged(auctionId, actingOsuId).flatMap(auction -> {
            if (auction.isManager(osuId)) {
                return Mono.error(new ConflictException("That user already manages this auction"));
            }
            return osuApi.fetchUser(osuId).flatMap(profile -> {
                Manager manager = Manager.builder()
                        .id(UUID.randomUUID()).osuId(profile.osuId()).username(profile.username())
                        .avatarUrl(profile.avatarUrl()).countryCode(profile.countryCode())
                        .discordId(blankToNull(discordId)).owner(false).addedAt(Instant.now())
                        .build();
                auction.getManagers().add(manager);
                return auctionRepository.save(auction).thenReturn(manager);
            });
        });
    }

    @Override
    public Mono<Void> removeManager(long actingOsuId, UUID auctionId, UUID managerId) {
        return loadManaged(auctionId, actingOsuId).flatMap(auction -> {
            Manager manager = auction.getManagers().stream()
                    .filter(m -> m.getId().equals(managerId)).findFirst()
                    .orElseThrow(() -> new NotFoundException("Manager not found"));
            if (manager.isOwner()) {
                return Mono.error(new ForbiddenException("The owner cannot be removed"));
            }
            auction.getManagers().removeIf(m -> m.getId().equals(managerId));
            return auctionRepository.save(auction).then();
        });
    }

    // ---- players ---------------------------------------------------------------------------

    private static final Pattern BEATMAP_ANCHOR = Pattern.compile("#\\w+/(\\d+)");
    private static final Pattern BEATMAP_PATH = Pattern.compile("/(?:beatmaps|b)/(\\d+)");
    private static final Pattern ANY_NUMBER = Pattern.compile("(\\d+)");

    @Override
    public Mono<Player> addPlayer(long actingOsuId, UUID auctionId, AddPlayerCommand command) {
        return editable(auctionId, actingOsuId).flatMap(auction -> {
            int maxLen = auction.getSettings().getMaxDescriptionLength();
            if (command.description() != null && command.description().length() > maxLen) {
                return Mono.error(new BadRequestException("Description exceeds " + maxLen + " characters"));
            }
            if (auction.findPlayerByOsuId(command.osuId()).isPresent()) {
                return Mono.error(new ConflictException("That player is already in the auction"));
            }
            return osuApi.fetchUser(command.osuId())
                    .flatMap(profile -> enrichWithQualifiers(playerFromProfile(profile, command.description()), command))
                    .flatMap(player -> {
                        auction.getPlayers().add(player);
                        return auctionRepository.save(auction).thenReturn(player);
                    });
        });
    }

    /** Populates the qualifier fields, resolving beatmap titles/covers from the osu! API (best-effort). */
    private Mono<Player> enrichWithQualifiers(Player player, AddPlayerCommand command) {
        player.setQualificationRank(command.qualificationRank());
        player.setBestMapAccuracy(command.bestAccuracy());
        player.setWorstMapAccuracy(command.worstAccuracy());

        Mono<Player> result = Mono.just(player);
        Long bestId = parseBeatmapId(command.bestBeatmapUrl());
        if (bestId != null) {
            result = result.flatMap(p -> osuApi.fetchBeatmap(bestId)
                    .doOnNext(bm -> {
                        p.setBestMapName(bm.title());
                        p.setBestMapImage(bm.coverUrl());
                    })
                    .thenReturn(p)
                    .onErrorReturn(p));
        }
        Long worstId = parseBeatmapId(command.worstBeatmapUrl());
        if (worstId != null) {
            result = result.flatMap(p -> osuApi.fetchBeatmap(worstId)
                    .doOnNext(bm -> {
                        p.setWorstMapName(bm.title());
                        p.setWorstMapImage(bm.coverUrl());
                    })
                    .thenReturn(p)
                    .onErrorReturn(p));
        }
        return result;
    }

    /** Extracts a beatmap (difficulty) id from a raw id or any osu! beatmap/beatmapset URL. */
    static Long parseBeatmapId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String s = url.trim();
        if (s.matches("\\d+")) {
            return Long.parseLong(s);
        }
        Matcher anchor = BEATMAP_ANCHOR.matcher(s);
        if (anchor.find()) {
            return Long.parseLong(anchor.group(1));
        }
        Matcher path = BEATMAP_PATH.matcher(s);
        if (path.find()) {
            return Long.parseLong(path.group(1));
        }
        Matcher any = ANY_NUMBER.matcher(s);
        Long last = null;
        while (any.find()) {
            last = Long.parseLong(any.group(1));
        }
        return last;
    }

    /**
     * Bulk import. Rather than one osu! API call per row, this validates everything synchronously,
     * collects all (distinct) osu! ids and beatmap ids, then fetches them with the BATCH endpoints
     * ({@code GET /users}, {@code GET /beatmaps}, 50 ids/request) — so a 100-row CSV needs a handful
     * of requests instead of hundreds. Players are then assembled from the in-memory result maps.
     */
    @Override
    public Mono<ImportPlayersResult> importPlayers(long actingOsuId, UUID auctionId, List<ImportPlayerRow> rows) {
        return editable(auctionId, actingOsuId).flatMap(auction -> {
            int maxLen = auction.getSettings().getMaxDescriptionLength();
            List<ImportPlayersResult.RowError> errors = new ArrayList<>();
            List<ValidatedRow> valid = validateRows(auction, rows, maxLen, errors);
            if (valid.isEmpty()) {
                return Mono.just(new ImportPlayersResult(List.of(), errors));
            }
            List<Long> osuIds = valid.stream().map(ValidatedRow::osuId).distinct().toList();
            List<Long> beatmapIds = valid.stream()
                    .flatMap(v -> Stream.of(v.bestBeatmapId(), v.worstBeatmapId()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            // Degrade gracefully (as the old per-row import did): a total user-lookup/token failure
            // falls back to an empty map so every row is reported as a per-row miss rather than
            // erroring the whole mutation; beatmap enrichment is best-effort and never blocks import.
            return Mono.zip(
                            osuApi.fetchUsers(osuIds)
                                    .onErrorResume(e -> {
                                        log.warn("Batch osu! user lookup failed during import: {}", e.toString());
                                        return Mono.just(Map.<Long, OsuUserProfile>of());
                                    }),
                            osuApi.fetchBeatmaps(beatmapIds)
                                    .onErrorReturn(Map.<Long, OsuBeatmap>of()))
                    .flatMap(t -> buildAndSave(auction, valid, t.getT1(), t.getT2(), errors));
        });
    }

    /** Synchronous, I/O-free per-row validation; returns the rows that are ready to be fetched. */
    private List<ValidatedRow> validateRows(Auction auction, List<ImportPlayerRow> rows, int maxLen,
                                            List<ImportPlayersResult.RowError> errors) {
        List<ValidatedRow> valid = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int line = 0;
        for (ImportPlayerRow row : rows) {
            line++;
            if (isBlank(row.username())) {
                errors.add(rowError(line, row, "username is required"));
                continue;
            }
            if (isBlank(row.osuId())) {
                errors.add(rowError(line, row, "osuId is required"));
                continue;
            }
            long osuId;
            try {
                osuId = Long.parseLong(row.osuId().trim());
            } catch (NumberFormatException e) {
                errors.add(rowError(line, row, "osuId must be numeric"));
                continue;
            }
            if (isBlank(row.qualificationRank())) {
                errors.add(rowError(line, row, "qualificationRank is required"));
                continue;
            }
            int qualificationRank;
            try {
                qualificationRank = Integer.parseInt(row.qualificationRank().trim());
            } catch (NumberFormatException e) {
                errors.add(rowError(line, row, "qualificationRank must be a whole number"));
                continue;
            }
            if (row.description() != null && row.description().length() > maxLen) {
                errors.add(rowError(line, row, "description exceeds " + maxLen + " characters"));
                continue;
            }
            Double bestAccuracy;
            Double worstAccuracy;
            try {
                bestAccuracy = parseOptionalAccuracy(row.bestBeatmapAccuracy());
                worstAccuracy = parseOptionalAccuracy(row.worstBeatmapAccuracy());
            } catch (NumberFormatException e) {
                errors.add(rowError(line, row, "accuracy must be a number"));
                continue;
            }
            if (!seen.add(osuId) || auction.findPlayerByOsuId(osuId).isPresent()) {
                errors.add(rowError(line, row, "already in the auction"));
                continue;
            }
            AddPlayerCommand command = new AddPlayerCommand(osuId, row.description(),
                    blankToNull(row.bestBeatmapUrl()), bestAccuracy,
                    blankToNull(row.worstBeatmapUrl()), worstAccuracy, qualificationRank);
            valid.add(new ValidatedRow(line, row, osuId, command,
                    parseBeatmapId(row.bestBeatmapUrl()), parseBeatmapId(row.worstBeatmapUrl())));
        }
        return valid;
    }

    /** Assembles players from the batch-fetched maps; a row whose user wasn't returned is a miss. */
    private Mono<ImportPlayersResult> buildAndSave(Auction auction, List<ValidatedRow> valid,
                                                   Map<Long, OsuUserProfile> users,
                                                   Map<Long, OsuBeatmap> maps,
                                                   List<ImportPlayersResult.RowError> errors) {
        List<Player> imported = new ArrayList<>();
        for (ValidatedRow v : valid) {
            OsuUserProfile profile = users.get(v.osuId());
            if (profile == null) {
                errors.add(rowError(v.line(), v.raw(), "osu! lookup failed: user " + v.osuId() + " not found"));
                continue;
            }
            Player player = playerFromProfile(profile, v.raw().description());
            applyQualifiers(player, v.command(), v.bestBeatmapId(), v.worstBeatmapId(), maps);
            auction.getPlayers().add(player);
            imported.add(player);
        }
        if (imported.isEmpty()) {
            return Mono.just(new ImportPlayersResult(imported, errors));
        }
        return auctionRepository.save(auction).thenReturn(new ImportPlayersResult(imported, errors));
    }

    /** Sets a player's qualifier fields from already-fetched beatmaps (no I/O; best-effort). */
    private void applyQualifiers(Player player, AddPlayerCommand cmd, Long bestId, Long worstId,
                                 Map<Long, OsuBeatmap> maps) {
        player.setQualificationRank(cmd.qualificationRank());
        player.setBestMapAccuracy(cmd.bestAccuracy());
        player.setWorstMapAccuracy(cmd.worstAccuracy());
        if (bestId != null) {
            OsuBeatmap bm = maps.get(bestId);
            if (bm != null) {
                player.setBestMapName(bm.title());
                player.setBestMapImage(bm.coverUrl());
            }
        }
        if (worstId != null) {
            OsuBeatmap bm = maps.get(worstId);
            if (bm != null) {
                player.setWorstMapName(bm.title());
                player.setWorstMapImage(bm.coverUrl());
            }
        }
    }

    private static ImportPlayersResult.RowError rowError(int line, ImportPlayerRow row, String reason) {
        return new ImportPlayersResult.RowError(line, row.osuId(), row.username(), reason);
    }

    /** A CSV row that passed validation, with parsed ids ready for the batch fetch. */
    private record ValidatedRow(int line, ImportPlayerRow raw, long osuId, AddPlayerCommand command,
                                Long bestBeatmapId, Long worstBeatmapId) {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Parses an optional accuracy cell; null/blank ⇒ null, otherwise a double (throws if not numeric). */
    private static Double parseOptionalAccuracy(String value) {
        if (isBlank(value)) {
            return null;
        }
        return Double.parseDouble(value.trim());
    }

    private Player playerFromProfile(OsuUserProfile profile, String description) {
        return Player.builder()
                .id(UUID.randomUUID())
                .osuId(profile.osuId())
                .username(profile.username())
                .avatarUrl(profile.avatarUrl())
                .bannerUrl(profile.bannerUrl())
                .countryCode(profile.countryCode())
                .globalRank(profile.globalRank())
                .countryRank(profile.countryRank())
                .description(description == null ? "" : description.trim())
                .status(PlayerStatus.AVAILABLE)
                .addedAt(Instant.now())
                .build();
    }

    @Override
    public Mono<Void> removePlayer(long actingOsuId, UUID auctionId, UUID playerId) {
        return editable(auctionId, actingOsuId).flatMap(auction -> {
            Player player = auction.findPlayer(playerId)
                    .orElseThrow(() -> new NotFoundException("Player not found"));
            auction.getPlayers().removeIf(p -> p.getId().equals(playerId));
            auction.getCaptains().removeIf(c -> c.getPlayerId().equals(playerId));
            return auctionRepository.save(auction).then();
        });
    }

    // ---- captains --------------------------------------------------------------------------

    @Override
    public Mono<Player> setCaptain(long actingOsuId, UUID auctionId, UUID playerId, String discordId) {
        return editable(auctionId, actingOsuId).flatMap(auction -> {
            if (discordId == null || discordId.isBlank()) {
                return Mono.error(new BadRequestException("A Discord id is required to flag a captain"));
            }
            Player player = auction.findPlayer(playerId)
                    .orElseThrow(() -> new NotFoundException("Player not found"));
            player.setCaptain(true);
            player.setDiscordId(discordId.trim());
            player.setStatus(PlayerStatus.AVAILABLE);
            auction.getCaptains().removeIf(c -> c.getPlayerId().equals(playerId));
            auction.getCaptains().add(Captain.builder()
                    .id(UUID.randomUUID())
                    .playerId(player.getId())
                    .osuId(player.getOsuId())
                    .username(player.getUsername())
                    .avatarUrl(player.getAvatarUrl())
                    .countryCode(player.getCountryCode())
                    .discordId(discordId.trim())
                    .balance(auction.getSettings().getStartingBalance())
                    .teamPlayerIds(new ArrayList<>())
                    .build());
            return auctionRepository.save(auction).thenReturn(player);
        });
    }

    @Override
    public Mono<Player> unsetCaptain(long actingOsuId, UUID auctionId, UUID playerId) {
        return editable(auctionId, actingOsuId).flatMap(auction -> {
            Player player = auction.findPlayer(playerId)
                    .orElseThrow(() -> new NotFoundException("Player not found"));
            player.setCaptain(false);
            player.setDiscordId(null);
            auction.getCaptains().removeIf(c -> c.getPlayerId().equals(playerId));
            return auctionRepository.save(auction).thenReturn(player);
        });
    }

    // ---- shared helpers --------------------------------------------------------------------

    private Mono<Auction> load(UUID auctionId) {
        return auctionRepository.findById(auctionId)
                .switchIfEmpty(Mono.error(new NotFoundException("Auction not found")));
    }

    private Mono<Auction> loadManaged(UUID auctionId, long actingOsuId) {
        return load(auctionId).flatMap(auction -> {
            if (!auction.isManager(actingOsuId) && !auction.isOwner(actingOsuId)) {
                return Mono.error(new ForbiddenException("You are not a manager of this auction"));
            }
            return Mono.just(auction);
        });
    }

    /** Loaded, manager-authorized and verified to still be editable (scheduled). */
    private Mono<Auction> editable(UUID auctionId, long actingOsuId) {
        return loadManaged(auctionId, actingOsuId).map(auction -> {
            requireScheduled(auction);
            return auction;
        });
    }

    private void requireScheduled(Auction auction) {
        if (auction.getState() != AuctionState.SCHEDULED) {
            throw new ConflictException("The auction has already started and can no longer be edited");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
