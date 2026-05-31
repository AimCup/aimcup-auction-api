package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.OsuBeatmap;
import xyz.aimcup.auction.domain.model.OsuUserProfile;

/**
 * Outbound port to the osu! API for looking up arbitrary users and beatmaps by id.
 */
public interface OsuApiPort {

    /** Fetches public profile data for an osu! user id. Errors if the user does not exist. */
    Mono<OsuUserProfile> fetchUser(long osuId);

    /** Fetches title + cover image for a beatmap id. Errors if the beatmap does not exist. */
    Mono<OsuBeatmap> fetchBeatmap(long beatmapId);
}
