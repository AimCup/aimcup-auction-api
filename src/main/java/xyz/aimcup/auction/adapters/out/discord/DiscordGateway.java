package xyz.aimcup.auction.adapters.out.discord;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.common.util.Snowflake;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.Color;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.BidEvent;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.port.out.DiscordNotificationPort;

import java.util.List;

/**
 * Owns the Discord gateway connection and publishes live auction events into the configured channel.
 * Has no inbound dependencies (it does not know about use cases), which keeps the dependency graph
 * acyclic — {@link DiscordCommandRouter} handles the inbound direction.
 *
 * <p>The connection is established asynchronously on startup; if Discord is disabled or the login
 * fails, every notification degrades to a no-op so the auction is never blocked.
 */
@Slf4j
@Component
public class DiscordGateway implements DiscordNotificationPort {

    private final AuctionProperties.Discord props;
    /** Completes with the connected client, or stays empty when Discord is unavailable. */
    private final Mono<GatewayDiscordClient> clientMono;

    public DiscordGateway(AuctionProperties properties) {
        this.props = properties.getDiscord();
        if (props.isEnabled() && props.getBotToken() != null && !props.getBotToken().isBlank()) {
            // MESSAGE_CONTENT is a privileged intent; it must also be enabled in the Discord
            // developer portal for the bot, otherwise the gateway rejects the connection. It is
            // required to relay channel messages to the stream overlay's live feed.
            IntentSet intents = props.isMessageContentIntent()
                    ? IntentSet.nonPrivileged().or(IntentSet.of(Intent.MESSAGE_CONTENT))
                    : IntentSet.nonPrivileged();
            this.clientMono = DiscordClient.create(props.getBotToken())
                    .gateway()
                    .setEnabledIntents(intents)
                    .login()
                    .doOnNext(c -> log.info("Discord gateway connected"))
                    .onErrorResume(e -> {
                        log.warn("Discord gateway login failed, notifications disabled: {}", e.toString());
                        return Mono.empty();
                    })
                    .cache();
        } else {
            log.info("Discord integration disabled");
            this.clientMono = Mono.empty();
        }
    }

    @PostConstruct
    void connect() {
        // Trigger the (cached) connection eagerly so the gateway is ready before the first event.
        clientMono.subscribe();
    }

    Mono<GatewayDiscordClient> client() {
        return clientMono;
    }

    // Brand palette (matches the web app): mint green, deep red, charcoal accents.
    private static final Color GREEN = Color.of(0x00CC99);
    private static final Color RED = Color.of(0xCA191B);
    private static final Color GOLD = Color.of(0xF5A623);
    private static final Color GREY = Color.of(0x6B7280);
    private static final Color BLURPLE = Color.of(0x5865F2);

    /** Sends an embed-only message to the auction's channel (no-op when no channel is configured). */
    private Mono<Void> sendEmbed(Auction auction, EmbedCreateSpec embed) {
        return dispatch(auction, MessageCreateSpec.builder().addEmbed(embed).build());
    }

    /**
     * Sends an embed with leading plain-text content. Used only for the readiness reminder, because
     * user mentions inside an embed do not trigger a notification — the ping must live in the content.
     */
    private Mono<Void> sendEmbed(Auction auction, String content, EmbedCreateSpec embed) {
        return dispatch(auction, MessageCreateSpec.builder().content(content).addEmbed(embed).build());
    }

    private Mono<Void> dispatch(Auction auction, MessageCreateSpec message) {
        if (auction.getChannelId() == null || auction.getChannelId().isBlank()) {
            return Mono.empty();
        }
        return clientMono.flatMap(client -> client.getChannelById(Snowflake.of(auction.getChannelId()))
                        .ofType(MessageChannel.class)
                        .flatMap(channel -> channel.createMessage(message)))
                .doOnError(e -> log.warn("Discord message failed: {}", e.toString()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private static String avatar(Player player) {
        if (player.getAvatarUrl() != null && !player.getAvatarUrl().isBlank()) {
            return player.getAvatarUrl();
        }
        return player.getOsuId() == null ? null : "https://a.ppy.sh/" + player.getOsuId();
    }

    @Override
    public Mono<Void> auctionStarted(Auction auction) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(GREEN)
                .title("Auction started")
                .description(auction.getName() + " is now live. Captains, place your bids with /bid "
                        + "or win instantly with /maxbid.")
                .build());
    }

    @Override
    public Mono<Void> playerUp(Auction auction, Player player, int stageIndex) {
        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                .color(BLURPLE)
                .author("Now up for auction — stage " + (stageIndex + 1), null, null)
                .title(player.getUsername());
        String avatar = avatar(player);
        if (avatar != null) {
            embed.thumbnail(avatar);
        }
        if (player.getDescription() != null && !player.getDescription().isBlank()) {
            embed.description(player.getDescription());
        }
        if (player.getGlobalRank() != null) {
            embed.addField("Global rank", "#" + player.getGlobalRank(), true);
        }
        if (player.getCountryRank() != null) {
            embed.addField("Country rank", "#" + player.getCountryRank(), true);
        }
        if (player.getQualificationRank() != null) {
            embed.addField("Qualifier rank", "#" + player.getQualificationRank(), true);
        }
        embed.footer("Bid with /bid amount:<credits> or /maxbid", null);
        return sendEmbed(auction, embed.build());
    }

    @Override
    public Mono<Void> bidPlaced(Auction auction, Player player, BidEvent bid) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(bid.isMaxBid() ? RED : GOLD)
                .title(bid.isMaxBid() ? "Max bid" : "New bid")
                .description(bid.getCaptainUsername() + " bids " + bid.getAmount()
                        + " on " + player.getUsername() + (bid.isMaxBid() ? " (instant win)" : ""))
                .build());
    }

    @Override
    public Mono<Void> playerSold(Auction auction, Player player, Captain winner, int price) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(GREEN)
                .title("Sold")
                .description(player.getUsername() + " joins " + winner.getUsername() + ".")
                .addField("Price", String.valueOf(price), true)
                .addField("Captain balance", String.valueOf(winner.getBalance()), true)
                .build());
    }

    @Override
    public Mono<Void> playerUnsold(Auction auction, Player player) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(GREY)
                .title("Unsold")
                .description(player.getUsername() + " received no bids.")
                .build());
    }

    @Override
    public Mono<Void> auctionPaused(Auction auction) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(GOLD)
                .title("Auction paused")
                .description("An organizer paused the auction. It will resume shortly.")
                .build());
    }

    @Override
    public Mono<Void> auctionResumed(Auction auction) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(GREEN)
                .title("Auction resumed")
                .description("The auction has resumed.")
                .build());
    }

    @Override
    public Mono<Void> auctionFinished(Auction auction) {
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(BLURPLE)
                .title("Auction finished")
                .description(auction.getName() + " has finished. Thanks for playing.")
                .build());
    }

    @Override
    public Mono<Void> captainReadyChanged(Auction auction, Captain captain, boolean ready,
                                          int readyCount, int totalCaptains) {
        String who = captain.getUsername() == null ? "A captain" : captain.getUsername();
        return sendEmbed(auction, EmbedCreateSpec.builder()
                .color(ready ? GREEN : GREY)
                .title(ready ? "Captain ready" : "Readiness cleared")
                .description(who + (ready ? " is ready." : " is no longer ready."))
                .addField("Captains ready", readyCount + "/" + totalCaptains, false)
                .build());
    }

    @Override
    public Mono<Void> remindNotReady(Auction auction, List<Captain> notReadyCaptains) {
        if (notReadyCaptains == null || notReadyCaptains.isEmpty()) {
            return Mono.empty();
        }
        StringBuilder mentions = new StringBuilder();
        for (Captain c : notReadyCaptains) {
            if (c.getDiscordId() != null && !c.getDiscordId().isBlank()) {
                mentions.append("<@").append(c.getDiscordId()).append("> ");
            } else if (c.getUsername() != null) {
                mentions.append(c.getUsername()).append(" ");
            }
        }
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                .color(GOLD)
                .title("Readiness reminder")
                .description("Please confirm you are ready with /ready. Still waiting on "
                        + notReadyCaptains.size() + " captain(s).")
                .build();
        // Mentions must be in the message content to actually ping; the details stay in the embed.
        return sendEmbed(auction, mentions.toString().trim(), embed);
    }
}
