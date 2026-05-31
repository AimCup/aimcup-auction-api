package xyz.aimcup.auction.domain.model;

/**
 * Minimal beatmap metadata fetched from the osu! API, used to render a player's best/worst
 * qualifier maps (title + cover image). Decouples the domain from the osu! JSON shape.
 */
public record OsuBeatmap(
        long beatmapId,
        String title,
        String coverUrl
) {
}
