package xyz.aimcup.auction.domain.model;

/**
 * Live runtime phase of a running auction. Unlike {@link AuctionState} this is transient
 * (kept in Redis / broadcast over subscriptions) and never persisted to the primary store.
 */
public enum AuctionPhase {
    /** Auction not started yet. */
    WAITING_TO_START,
    /** A player is currently up for bids. */
    BIDDING,
    /** Cool-down window between two players. */
    GAP,
    /** Halted by an organizer. */
    PAUSED,
    /** Auction complete. */
    FINISHED
}
