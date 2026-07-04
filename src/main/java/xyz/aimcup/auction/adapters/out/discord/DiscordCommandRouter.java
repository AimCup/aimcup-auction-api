package xyz.aimcup.auction.adapters.out.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.Manager;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.port.in.AuctionChannelLookup;
import xyz.aimcup.auction.domain.port.in.AuctionControlUseCase;
import xyz.aimcup.auction.domain.port.in.BiddingUseCase;
import xyz.aimcup.auction.domain.port.in.ReadinessUseCase;
import xyz.aimcup.auction.domain.port.in.command.BidActor;

import java.util.List;
import java.util.Optional;

/**
 * Registers and dispatches Discord slash commands. {@code /bid} and {@code /maxbid} are open to
 * captains; {@code /pause}, {@code /resume}, {@code /removeplayer} and {@code /setbalance} are
 * restricted to organizers (managers). Authorisation is enforced here (by mapping the Discord user
 * to a captain/manager of the channel's auction) and again inside the use cases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordCommandRouter {

    private final AuctionProperties properties;
    private final DiscordGateway gateway;
    private final BiddingUseCase biddingUseCase;
    private final AuctionControlUseCase controlUseCase;
    private final ReadinessUseCase readinessUseCase;
    private final AuctionChannelLookup channelLookup;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.getDiscord().isEnabled()) {
            return;
        }
        gateway.client()
                .flatMap(client -> registerCommands(client).thenReturn(client))
                .flatMapMany(client -> client.on(ChatInputInteractionEvent.class))
                .flatMap(this::handle)
                .onErrorContinue((e, o) -> log.warn("Discord command error: {}", e.toString()))
                .subscribe();
    }

    private Mono<Void> registerCommands(GatewayDiscordClient client) {
        List<ApplicationCommandRequest> commands = List.of(
                ApplicationCommandRequest.builder()
                        .name("bid").description("Bid on the player currently up for auction")
                        .addOption(intOption("amount", "Amount of credits to bid", true))
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("maxbid").description("Instantly win the current player with the configured max bid")
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("ready").description("[Captain] Confirm you are ready for the auction to start")
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("pause").description("[Organizer] Pause the auction after the current player")
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("resume").description("[Organizer] Resume a paused auction")
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("removeplayer").description("[Organizer] Remove a sold player from their team and refund")
                        .addOption(intOption("osu_id", "osu! id of the player to remove", true))
                        .build(),
                ApplicationCommandRequest.builder()
                        .name("setbalance").description("[Organizer] Override a captain's balance")
                        .addOption(intOption("captain_osu_id", "osu! id of the captain", true))
                        .addOption(intOption("amount", "New balance", true))
                        .build()
        );
        return client.getRestClient().getApplicationId()
                .flatMapMany(appId -> Flux.fromIterable(commands)
                        .flatMap(cmd -> client.getRestClient().getApplicationService()
                                .createGlobalApplicationCommand(appId, cmd)))
                .doOnComplete(() -> log.info("Registered {} Discord slash commands", commands.size()))
                .then();
    }

    private static ApplicationCommandOptionData intOption(String name, String description, boolean required) {
        return ApplicationCommandOptionData.builder()
                .name(name).description(description)
                .type(ApplicationCommandOption.Type.INTEGER.getValue())
                .required(required)
                .build();
    }

    private Mono<Void> handle(ChatInputInteractionEvent event) {
        String channelId = event.getInteraction().getChannelId().asString();
        String discordId = event.getInteraction().getUser().getId().asString();
        return channelLookup.findActiveByChannelId(channelId)
                // No active auction in THIS instance: stay silent instead of replying "no active
                // auction". The same bot token can be connected from more than one instance
                // (e.g. a local dev run and the deployed env, or next + prod); every instance
                // receives the interaction, but only the one actually running the auction can place
                // the bid. Replying here produced the confusing "no active auction" while the bid
                // still went through on the owning instance.
                .switchIfEmpty(Mono.<Auction>fromRunnable(() -> log.debug(
                        "Ignoring /{} in channel {}: no auction is active on this instance",
                        event.getCommandName(), channelId)))
                .flatMap(auction -> dispatch(event, auction, discordId))
                .onErrorResume(e -> reply(event, e.getMessage()));
    }

    private Mono<Void> dispatch(ChatInputInteractionEvent event, Auction auction, String discordId) {
        return switch (event.getCommandName()) {
            case "bid" -> {
                long amount = longOption(event, "amount").orElse(0L);
                yield biddingUseCase.placeBid(auction.getId(), BidActor.discord(discordId), (int) amount, "DISCORD")
                        .flatMap(r -> reply(event, r.message()));
            }
            case "maxbid" -> biddingUseCase.placeMaxBid(auction.getId(), BidActor.discord(discordId), "DISCORD")
                    .flatMap(r -> reply(event, r.message()));
            case "ready" -> readinessUseCase.setReady(auction.getId(), BidActor.discord(discordId), true)
                    .flatMap(r -> reply(event, "You are marked ready. (" + r.readyCount() + "/"
                            + r.totalCaptains() + " captains ready)"));
            case "pause" -> asManager(event, auction, discordId, mgr ->
                    controlUseCase.pause(mgr.getOsuId(), auction.getId())
                            .then(reply(event, "Auction paused.")));
            case "resume" -> asManager(event, auction, discordId, mgr ->
                    controlUseCase.resume(mgr.getOsuId(), auction.getId())
                            .then(reply(event, "Auction resumed.")));
            case "removeplayer" -> asManager(event, auction, discordId, mgr -> {
                long osuId = longOption(event, "osu_id").orElse(0L);
                Optional<Player> player = auction.findPlayerByOsuId(osuId);
                if (player.isEmpty()) {
                    return reply(event, "No player with osu! id " + osuId + " in this auction.");
                }
                return controlUseCase.removePlayerFromTeam(mgr.getOsuId(), auction.getId(), player.get().getId())
                        .then(reply(event, "Removed " + player.get().getUsername() + " from their team and refunded."));
            });
            case "setbalance" -> asManager(event, auction, discordId, mgr -> {
                long captainOsuId = longOption(event, "captain_osu_id").orElse(0L);
                long amount = longOption(event, "amount").orElse(0L);
                Optional<Captain> captain = auction.getCaptains().stream()
                        .filter(c -> c.getOsuId() != null && c.getOsuId() == captainOsuId).findFirst();
                if (captain.isEmpty()) {
                    return reply(event, "No captain with osu! id " + captainOsuId + " in this auction.");
                }
                return controlUseCase.changeCaptainBalance(mgr.getOsuId(), auction.getId(), captain.get().getId(), (int) amount)
                        .then(reply(event, "Set " + captain.get().getUsername() + "'s balance to " + amount + "."));
            });
            default -> reply(event, "Unknown command.");
        };
    }

    private Mono<Void> asManager(ChatInputInteractionEvent event, Auction auction, String discordId,
                                 java.util.function.Function<Manager, Mono<Void>> action) {
        return auction.findManagerByDiscordId(discordId)
                .map(action)
                .orElseGet(() -> reply(event, "Only organizers can use this command."));
    }

    private Mono<Void> reply(ChatInputInteractionEvent event, String content) {
        return event.reply().withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder().description(content).build());
    }

    private Optional<Long> longOption(ChatInputInteractionEvent event, String name) {
        return event.getOption(name)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong);
    }
}
