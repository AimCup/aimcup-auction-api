package xyz.aimcup.auction.domain.port.in.command;

/**
 * Payload to add a single player to an auction. Beyond the osu! id and description it optionally
 * carries qualifier data: a link to the player's best/worst qualifier beatmap together with the
 * accuracy scored on it, and the final qualifier ranking. Beatmap titles and cover images are
 * resolved from the osu! API at add time.
 */
public record AddPlayerCommand(
        long osuId,
        String description,
        String bestBeatmapUrl,
        Double bestAccuracy,
        String worstBeatmapUrl,
        Double worstAccuracy,
        Integer qualificationRank
) {
}
