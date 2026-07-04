package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.OsuBeatmap;
import xyz.aimcup.auction.domain.model.OsuUserProfile;

import java.util.Collection;
import java.util.Map;

/**
 * Outbound port to the osu! API for looking up arbitrary users and beatmaps by id.
 */
public interface OsuApiPort {

    /** Fetches public profile data for an osu! user id. Errors if the user does not exist. */
    Mono<OsuUserProfile> fetchUser(long osuId);

    /** Fetches title + cover image for a beatmap id. Errors if the beatmap does not exist. */
    Mono<OsuBeatmap> fetchBeatmap(long beatmapId);

    /**
     * Batch user lookup via {@code GET /users?ids[]=…}, chunked into requests of at most 50 ids.
     * The returned map is keyed by osu! id and contains <em>only</em> the ids the API returned a
     * profile for; unknown/restricted ids are simply absent (no error is raised for misses).
     *
     * <p>Note: profiles from this endpoint carry a {@code null} country rank — the batch endpoint
     * does not expose it (global rank is present). Use {@link #fetchUser(long)} when the country
     * rank is needed.
     */
    Mono<Map<Long, OsuUserProfile>> fetchUsers(Collection<Long> osuIds);

    /**
     * Batch beatmap lookup via {@code GET /beatmaps?ids[]=…}, chunked into requests of at most 50
     * ids. Keyed by beatmap id; unknown ids are absent from the map rather than raising an error.
     */
    Mono<Map<Long, OsuBeatmap>> fetchBeatmaps(Collection<Long> beatmapIds);
}
