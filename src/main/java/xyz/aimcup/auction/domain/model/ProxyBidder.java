package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An alternate identity that bids and confirms readiness on a {@link Captain}'s behalf. When a captain
 * has a proxy, ONLY the proxy's osu!/Discord identity may act for them — the captain themselves is
 * locked out, so the two can never bid at the same moment. A proxy is not a player in the pool.
 *
 * <p>Assigned by an organizer before the auction starts (or while it is scheduled); it can only be
 * revoked by an organizer, and while the auction is running only when it is paused.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProxyBidder {

    /** osu! id the proxy signs in / bids with from the web. */
    private Long osuId;
    /** Discord snowflake the proxy bids with from Discord (optional). */
    private String discordId;
    private String username;
    private String avatarUrl;
}
