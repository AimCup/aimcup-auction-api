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
    /**
     * A max bid was called for the current player. The price is locked at the max bid and other
     * captains have a short, configurable window ({@code maxBidWindowSeconds}) to counter with their
     * own max bid before the winner is drawn at random from everyone who maxed.
     */
    MAX_BID_WINDOW,
    /**
     * The max-bid window has closed and the winner has been drawn. A brief reveal window during which
     * the client plays the "which captain wins" animation; the player is awarded when it ends.
     */
    MAX_BID_DRAW,
    /** Cool-down window between two players. */
    GAP,
    /** Halted by an organizer. */
    PAUSED,
    /** Auction complete. */
    FINISHED
}
