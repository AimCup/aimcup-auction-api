package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A single bid placed on the player currently up for auction. Used to build the live bid-history
 * column; resets every time a new player comes up.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidEvent {

    private UUID captainId;
    private String captainUsername;
    private String captainAvatarUrl;
    private int amount;
    private Instant at;
    /** True when the bid was the instant-win "max bid". */
    @Builder.Default
    private boolean maxBid = false;
    /** "WEB" or "DISCORD" — where the bid originated. */
    @Builder.Default
    private String source = "WEB";
}
