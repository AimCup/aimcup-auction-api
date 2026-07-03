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
     * Seconds the auction waits after the first max bid for other captains to counter with their own
     * max bid. When the window closes the winner is drawn at random from everyone who maxed, so a max
     * bid is no longer first-come-first-served.
     *
     * <p>New auctions get 10 via {@link #defaults()} / the builder. A legacy document saved before this
     * field existed deserializes to 0 (Lombok's {@code @Builder.Default} does not apply on the no-arg
     * constructor Spring Data uses); the engine treats a non-positive window as the 10s default, and the
     * settings form normalises 0 → 10 on the next save.
     */
    @Builder.Default
    private int maxBidWindowSeconds = 10;

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

    /**
     * Largest roster a captain may build. Once a captain owns this many players they can no longer
     * bid. Must be at least {@link #teamSizeForPercentLimit}. A value of {@code 0} means "no limit".
     *
     * <p>Deliberately has no {@code @Builder.Default}: that would bake 8 into the no-arg constructor
     * Spring Data uses to deserialize, so a legacy document missing this key would come back as 8
     * instead of 0. Leaving it un-defaulted means absent ⇒ 0 (no limit); new auctions get their
     * default from {@link #defaults()}.
     */
    private int maxTeamSize;

    public static AuctionSettings defaults() {
        return AuctionSettings.builder().maxTeamSize(8).build();
    }
}
