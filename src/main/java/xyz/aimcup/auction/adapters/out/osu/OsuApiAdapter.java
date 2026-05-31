package xyz.aimcup.auction.adapters.out.osu;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.exception.NotFoundException;
import xyz.aimcup.auction.domain.model.OsuBeatmap;
import xyz.aimcup.auction.domain.model.OsuUserProfile;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Reactive osu! API client. Uses the client-credentials grant to obtain a public-scope token
 * (cached until shortly before expiry) and looks up users by id.
 */
@Slf4j
@Component
public class OsuApiAdapter implements xyz.aimcup.auction.domain.port.out.OsuApiPort {

    private static final String TOKEN_URI = "https://osu.ppy.sh/oauth/token";
    /** osu! batch endpoints cap at 50 ids per request (the server silently truncates beyond this). */
    private static final int MAX_IDS_PER_REQUEST = 50;
    /** Bound concurrent chunk requests so we stay polite to osu! rate limits. */
    private static final int MAX_CONCURRENT_CHUNKS = 4;

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

    @Override
    public Mono<Map<Long, OsuUserProfile>> fetchUsers(Collection<Long> osuIds) {
        List<Long> ids = distinct(osuIds);
        if (ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        return accessToken().flatMap(token -> Flux.fromIterable(chunk(ids, MAX_IDS_PER_REQUEST))
                .flatMap(batch -> getJson(batchUri("/users", batch), token).map(this::toProfileMap),
                        MAX_CONCURRENT_CHUNKS)
                .reduce(new HashMap<Long, OsuUserProfile>(), (acc, m) -> {
                    acc.putAll(m);
                    return acc;
                })
                .map(Collections::unmodifiableMap));
    }

    @Override
    public Mono<Map<Long, OsuBeatmap>> fetchBeatmaps(Collection<Long> beatmapIds) {
        List<Long> ids = distinct(beatmapIds);
        if (ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        return accessToken().flatMap(token -> Flux.fromIterable(chunk(ids, MAX_IDS_PER_REQUEST))
                .flatMap(batch -> getJson(batchUri("/beatmaps", batch), token).map(this::toBeatmapMap),
                        MAX_CONCURRENT_CHUNKS)
                .reduce(new HashMap<Long, OsuBeatmap>(), (acc, m) -> {
                    acc.putAll(m);
                    return acc;
                })
                .map(Collections::unmodifiableMap));
    }

    private Mono<JsonNode> getJson(URI uri, String token) {
        // The batch endpoints simply omit unknown ids (no per-id 404), so no error mapping here.
        return webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Builds {@code {base}{path}?ids[]=a&ids[]=b…} with the brackets pre-encoded as {@code %5B%5D}
     * (which the osu! API accepts) so the value is a valid {@link URI} and WebClient sends it as-is.
     */
    private URI batchUri(String path, List<Long> ids) {
        String query = ids.stream().map(id -> "ids%5B%5D=" + id).collect(Collectors.joining("&"));
        return URI.create(osuProps.getApi().getBaseUrl() + path + "?" + query);
    }

    private Map<Long, OsuUserProfile> toProfileMap(JsonNode node) {
        Map<Long, OsuUserProfile> out = new HashMap<>();
        JsonNode users = node == null ? null : node.get("users");
        if (users != null && users.isArray()) {
            for (JsonNode u : users) {
                if (u.hasNonNull("id")) {
                    long id = u.get("id").asLong();
                    out.put(id, toProfileCompact(id, u));
                }
            }
        }
        return out;
    }

    private Map<Long, OsuBeatmap> toBeatmapMap(JsonNode node) {
        Map<Long, OsuBeatmap> out = new HashMap<>();
        JsonNode beatmaps = node == null ? null : node.get("beatmaps");
        if (beatmaps != null && beatmaps.isArray()) {
            for (JsonNode b : beatmaps) {
                if (b.hasNonNull("id")) {
                    long id = b.get("id").asLong();
                    out.put(id, toBeatmap(id, b)); // same element shape as the single endpoint
                }
            }
        }
        return out;
    }

    private static List<Long> distinct(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static <T> List<List<T>> chunk(List<T> src, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < src.size(); i += size) {
            chunks.add(src.subList(i, Math.min(i + size, src.size())));
        }
        return chunks;
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

    /**
     * Maps one element of the batch {@code GET /users} response (a compact user). Its shape differs
     * from the single endpoint: there is no top-level {@code statistics} (ranks live under
     * {@code statistics_rulesets.osu}) and the banner is only at {@code cover.url}. The batch
     * endpoint does not expose {@code country_rank}, so it is left null here.
     */
    private OsuUserProfile toProfileCompact(long osuId, JsonNode node) {
        String username = text(node, "username");
        String avatarUrl = text(node, "avatar_url");
        String cover = node.hasNonNull("cover") ? text(node.get("cover"), "url") : null;
        String countryCode = text(node, "country_code");
        Long globalRank = null;
        Long countryRank = null;
        JsonNode rulesets = node.get("statistics_rulesets");
        if (rulesets != null && rulesets.hasNonNull("osu")) {
            JsonNode osu = rulesets.get("osu");
            globalRank = longOrNull(osu, "global_rank");
            countryRank = longOrNull(osu, "country_rank"); // absent on this endpoint → null
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
