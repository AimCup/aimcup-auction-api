package xyz.aimcup.auction.domain.port.in.command;

import xyz.aimcup.auction.domain.model.Player;

import java.util.List;

/**
 * Outcome of a CSV import: the players that were created and a per-row list of validation/lookup
 * errors so the UI can show exactly which rows failed and why.
 */
public record ImportPlayersResult(List<Player> imported, List<RowError> errors) {

    /** A single failed row; {@code line} is 1-based excluding the header. */
    public record RowError(int line, String osuId, String username, String reason) {
    }
}
