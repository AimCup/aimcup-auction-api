package xyz.aimcup.auction.domain.model;

/**
 * Immutable snapshot of a user fetched from the osu! API. Used to populate players, managers and
 * the logged-in user without coupling the domain to the osu! JSON shape.
 */
public record OsuUserProfile(
        long osuId,
        String username,
        String avatarUrl,
        String bannerUrl,
        String countryCode,
        Long globalRank,
        Long countryRank
) {
}
