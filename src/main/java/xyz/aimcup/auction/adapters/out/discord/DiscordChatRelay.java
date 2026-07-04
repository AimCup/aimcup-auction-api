package xyz.aimcup.auction.adapters.out.discord;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.ChatMessage;
import xyz.aimcup.auction.domain.port.in.ChatRelayUseCase;
import xyz.aimcup.auction.domain.port.out.AuctionRepositoryPort;

import java.time.Instant;
import java.util.List;

/**
 * Relays messages posted in an auction's linked Discord channel into the live feed shown on the
 * stream overlay. Listens to the gateway's {@code MessageCreateEvent} and fans each message out via
 * {@link ChatRelayUseCase} to every auction wired to that channel.
 *
 * <p>The displayed author is the sender's <em>server nickname</em> ({@link Member#getDisplayName()},
 * which is present on guild message events without the GUILD_MEMBERS intent), and any rich embed is
 * preserved in full so the overlay can render it one-to-one with Discord.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordChatRelay {

    private final AuctionProperties properties;
    private final DiscordGateway gateway;
    private final AuctionRepositoryPort auctionRepository;
    private final ChatRelayUseCase chatRelay;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.getDiscord().isEnabled()) {
            return;
        }
        gateway.client()
                .flatMapMany(client -> client.on(MessageCreateEvent.class))
                .flatMap(this::handle)
                .onErrorContinue((e, o) -> log.warn("Discord chat relay error: {}", e.toString()))
                .subscribe();
    }

    private Mono<Void> handle(MessageCreateEvent event) {
        // Drop the bot's own plain-text messages while keeping its embeds (bid/sold notifications) so
        // the feed still mirrors the channel. The bot currently posts only embeds, so this guard rarely
        // fires; it is kept defensively so any future plain-text bot post is never echoed into the feed.
        boolean ownEcho = event.getMessage().getAuthor()
                .map(a -> a.getId().equals(event.getClient().getSelfId()))
                .orElse(false)
                && event.getMessage().getEmbeds().isEmpty();
        if (ownEcho) {
            return Mono.empty();
        }
        ChatMessage chat = toChatMessage(event);
        if (chat == null) {
            return Mono.empty();
        }
        String channelId = event.getMessage().getChannelId().asString();
        return auctionRepository.findByChannelId(channelId)
                .doOnNext(auction -> chatRelay.publish(auction.getId(), chat))
                .then();
    }

    private ChatMessage toChatMessage(MessageCreateEvent event) {
        Message message = event.getMessage();
        // A message can carry several embeds, but the bot sends one per message; render the first.
        List<Embed> embeds = message.getEmbeds();
        ChatMessage.ChatEmbed embed = embeds.isEmpty() ? null : toEmbed(embeds.get(0));
        String content = cleanContent(message.getContent());
        if (content.isBlank() && embed == null) {
            return null;
        }
        // Prefer the server nickname (Member); fall back to the global username.
        String author = event.getMember().map(Member::getDisplayName)
                .or(() -> message.getAuthor().map(User::getUsername))
                .orElse("unknown");
        String avatar = event.getMember().map(Member::getEffectiveAvatarUrl)
                .or(() -> message.getAuthor().map(User::getAvatarUrl))
                .orElse(null);
        Instant at = message.getTimestamp() == null ? Instant.now() : message.getTimestamp();
        return new ChatMessage(author, content, avatar, at, embed);
    }

    private ChatMessage.ChatEmbed toEmbed(Embed e) {
        String color = e.getColor().map(c -> String.format("#%06X", c.getRGB() & 0xFFFFFF)).orElse(null);
        List<ChatMessage.ChatEmbedField> fields = e.getFields().stream()
                .map(f -> new ChatMessage.ChatEmbedField(f.getName(), f.getValue(), f.isInline()))
                .toList();
        return new ChatMessage.ChatEmbed(
                color,
                e.getAuthor().flatMap(Embed.Author::getName).orElse(null),
                e.getAuthor().flatMap(Embed.Author::getIconUrl).orElse(null),
                e.getTitle().orElse(null),
                e.getDescription().orElse(null),
                fields,
                e.getThumbnail().map(Embed.Thumbnail::getUrl).orElse(null),
                e.getImage().map(Embed.Image::getUrl).orElse(null),
                e.getFooter().map(Embed.Footer::getText).orElse(null),
                e.getFooter().flatMap(Embed.Footer::getIconUrl).orElse(null),
                e.getTimestamp().orElse(null));
    }

    /**
     * Turns raw Discord markup into something readable on the overlay: custom emoji become
     * {@code :name:} and user/role/channel mention tokens (which carry only numeric ids) are dropped.
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("<a?:(\\w+):\\d+>", ":$1:")
                .replaceAll("<@[!&]?\\d+>", "")
                .replaceAll("<#\\d+>", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
