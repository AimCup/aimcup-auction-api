package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Numeric bidding configuration for an auction. All amounts are expressed in the auction's
 * abstract "credits" currency.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuctionSettings {

    /** Credits every captain starts with. */
    @Builder.Default
    private int startingBalance = 14_000;

    /** A bid of exactly this amount instantly wins the player (the "max bid"). */
    @Builder.Default
    private int maxBid = 9_000;

    /** Smallest legal raise; every bid must be a multiple of it. */
    @Builder.Default
    private int minIncrement = 100;

    /**
     * Number of players a captain must already own before the {@link #maxBidPercent}
     * cap stops applying.
     */
    @Builder.Default
    private int teamSizeForPercentLimit = 3;

    /**
     * While a captain owns fewer than {@link #teamSizeForPercentLimit} players they may not bid
     * more than this percentage of their current balance, forcing them to keep funds for a full roster.
     */
    @Builder.Default
    private int maxBidPercent = 75;

    /** Maximum length of a player's free-text description. */
    @Builder.Default
    private int maxDescriptionLength = 360;

    public static AuctionSettings defaults() {
        return AuctionSettings.builder().build();
    }
}
