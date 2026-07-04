package xyz.aimcup.auction.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * A single message shown on an auction's live feed (stream overlay + public page). Every message is
 * relayed from the linked Discord channel.
 *
 * <p>{@link #author} is the sender's display name (the Discord server nickname). When the message
 * carries a rich embed it is preserved in {@link #embed} so the overlay can render it one-to-one with
 * how it appears in Discord.
 */
public record ChatMessage(
        String author,
        String content,
        String avatarUrl,
        Instant at,
        ChatEmbed embed
) {

    /** A Discord rich embed, mirroring the fields Discord renders. */
    public record ChatEmbed(
            /** Accent color as a hex string, e.g. {@code #00CC99} (null when the embed has no color). */
            String color,
            String authorName,
            String authorIcon,
            String title,
            String description,
            List<ChatEmbedField> fields,
            String thumbnail,
            String image,
            String footer,
            String footerIcon,
            Instant timestamp
    ) {
    }

    public record ChatEmbedField(String name, String value, boolean inline) {
    }
}
