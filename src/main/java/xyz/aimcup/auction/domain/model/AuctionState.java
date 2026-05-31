package xyz.aimcup.auction.domain.model;

/**
 * Persisted lifecycle state of an auction.
 */
public enum AuctionState {
    /** Created but not yet started — managers can still configure it. */
    SCHEDULED,
    /** Actively auctioning players. */
    RUNNING,
    /** Temporarily halted by an organizer; the current player finished, the next will not start. */
    PAUSED,
    /** All stages completed. */
    FINISHED
}
