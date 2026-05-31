package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One stage ("etap") of an auction. The first stage auctions every player; each subsequent stage
 * re-auctions the players that went unsold, in random order. Timing can differ per stage.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuctionStage {

    /** Zero-based position of this stage. */
    private int index;

    /** Seconds a player stays open for bids when no bid has been placed yet. */
    @Builder.Default
    private int biddingTimeSeconds = 30;

    /**
     * Seconds the player stays open after the first (and every subsequent) bid. The countdown
     * resets to this value on each new bid.
     */
    @Builder.Default
    private int biddingTimeAfterBidSeconds = 15;

    /** Cool-down between the end of one player and the start of the next. Never below 3. */
    @Builder.Default
    private int gapTimeSeconds = 5;
}
