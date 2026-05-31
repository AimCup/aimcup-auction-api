package xyz.aimcup.auction.adapters.out.osu;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.exception.NotFoundException;
import xyz.aimcup.auction.domain.model.OsuBeatmap;
import xyz.aimcup.auction.domain.model.OsuUserProfile;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive osu! API client. Uses the client-credentials grant to obtain a public-scope token
 * (cached until shortly before expiry) and looks up users by id.
 */
@Slf4j
@Component
public class OsuApiAdapter implements xyz.aimcup.auction.domain.port.out.OsuApiPort {

    private static final String TOKEN_URI = "https://osu.ppy.sh/oauth/token";

    private final WebClient webClient;
    private final AuctionProperties.Osu osuProps;
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    public OsuApiAdapter(WebClient.Builder webClientBuilder, AuctionProperties properties) {
        this.osuProps = properties.getOsu();
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<OsuUserProfile> fetchUser(long osuId) {
        return accessToken().flatMap(token -> webClient.get()
                .uri(osuProps.getApi().getBaseUrl() + "/users/{id}/osu", osuId)
                .header("Authorization", "Bearer " + token)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new NotFoundException("osu! user " + osuId + " not found")))
                .bodyToMono(JsonNode.class)
                .map(node -> toProfile(osuId, node)));
    }

    @Override
    public Mono<OsuBeatmap> fetchBeatmap(long beatmapId) {
        return accessToken().flatMap(token -> webClient.get()
                .uri(osuProps.getApi().getBaseUrl() + "/beatmaps/{id}", beatmapId)
                .header("Authorization", "Bearer " + token)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new NotFoundException("osu! beatmap " + beatmapId + " not found")))
                .bodyToMono(JsonNode.class)
                .map(node -> toBeatmap(beatmapId, node)));
    }

    private OsuBeatmap toBeatmap(long beatmapId, JsonNode node) {
        JsonNode set = node.get("beatmapset");
        String title = null;
        String cover = null;
        if (set != null && !set.isNull()) {
            String artist = text(set, "artist");
            String songTitle = text(set, "title");
            String version = text(node, "version");
            StringBuilder sb = new StringBuilder();
            if (artist != null) {
                sb.append(artist).append(" - ");
            }
            if (songTitle != null) {
                sb.append(songTitle);
            }
            if (version != null) {
                sb.append(" [").append(version).append("]");
            }
            title = sb.length() == 0 ? null : sb.toString();
            JsonNode covers = set.get("covers");
            if (covers != null && !covers.isNull()) {
                cover = text(covers, "cover@2x");
                if (cover == null) {
                    cover = text(covers, "cover");
                }
                if (cover == null) {
                    cover = text(covers, "card");
                }
            }
        }
        return new OsuBeatmap(beatmapId, title, cover);
    }

    private OsuUserProfile toProfile(long osuId, JsonNode node) {
        String username = text(node, "username");
        String avatarUrl = text(node, "avatar_url");
        String cover = text(node, "cover_url");
        if (cover == null && node.hasNonNull("cover")) {
            cover = text(node.get("cover"), "url");
        }
        String countryCode = text(node, "country_code");
        Long globalRank = null;
        Long countryRank = null;
        JsonNode stats = node.get("statistics");
        if (stats != null && !stats.isNull()) {
            globalRank = longOrNull(stats, "global_rank");
            countryRank = longOrNull(stats, "country_rank");
        }
        return new OsuUserProfile(osuId, username, avatarUrl, cover, countryCode, globalRank, countryRank);
    }

    // ---- token management ------------------------------------------------------

    private Mono<String> accessToken() {
        CachedToken cached = tokenCache.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Mono.just(cached.token());
        }
        return fetchToken();
    }

    private Mono<String> fetchToken() {
        return webClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("client_id", osuProps.getApi().getClientId())
                        .with("client_secret", osuProps.getApi().getClientSecret())
                        .with("grant_type", "client_credentials")
                        .with("scope", "public"))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    String token = text(node, "access_token");
                    long expiresIn = node.has("expires_in") ? node.get("expires_in").asLong() : 3600L;
                    CachedToken cached = new CachedToken(token, Instant.now().plusSeconds(expiresIn - 60));
                    tokenCache.set(cached);
                    log.info("Obtained new osu! API token (valid ~{}s)", expiresIn);
                    return token;
                });
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
