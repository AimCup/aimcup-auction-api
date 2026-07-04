package xyz.aimcup.auction.domain.port.in.command;

/**
 * One raw row from a CSV import. Columns, in order:
 * {@code username, osuId, description, qualificationRank, bestBeatmapUrl, bestBeatmapAccuracy,
 * worstBeatmapUrl, worstBeatmapAccuracy}. All values are the raw (string) cell contents; parsing
 * and validation happen in the import use case. {@code username}, {@code osuId} and
 * {@code qualificationRank} are required; the rest are optional.
 */
public record ImportPlayerRow(
        String username,
        String osuId,
        String description,
        String qualificationRank,
        String bestBeatmapUrl,
        String bestBeatmapAccuracy,
        String worstBeatmapUrl,
        String worstBeatmapAccuracy
) {
}
