package xyz.aimcup.auction.domain.model;

/**
 * Status of a player within an auction.
 */
public enum PlayerStatus {
    /** Not yet auctioned (or queued for a later stage). */
    AVAILABLE,
    /** Won by a captain and assigned to their team. */
    SOLD,
    /** Went through every stage without receiving a bid. */
    UNSOLD
}
