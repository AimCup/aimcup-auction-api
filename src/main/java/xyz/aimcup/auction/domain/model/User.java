package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A logged-in identity (one row per osu! account). Used to serve the {@code me} query and to know
 * who the authenticated principal is; auction-specific roles (manager / captain) live on the auction.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID id;
    private Long osuId;
    private String username;
    private String avatarUrl;
    private String countryCode;
    private Long globalRank;
    private Long countryRank;
    private Instant lastLoginAt;
}
