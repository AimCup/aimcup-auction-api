package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A person allowed to manage a single auction. Created per-auction (the same osu! account can be a
 * manager of many auctions independently). The {@code owner} has every right plus the exclusive
 * ability to delete the auction.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Manager {

    private UUID id;
    private Long osuId;
    private String username;
    private String avatarUrl;
    private String countryCode;
    /** Discord snowflake (string — exceeds 32-bit range). */
    private String discordId;
    /** True for the auction creator; owners cannot be removed and may delete the auction. */
    private boolean owner;
    private Instant addedAt;
}
