package xyz.aimcup.auction.domain.port.in.command;

/**
 * Outcome of a captain (un)marking themselves ready: the captain's new readiness plus how many of
 * the auction's captains are ready in total, so the UI/Discord can render a live counter.
 */
public record ReadyResult(boolean ready, int readyCount, int totalCaptains) {
}
