package xyz.aimcup.auction.adapters.out.osu;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.OsuUserProfile;

/**
 * Handles the osu! authorization-code grant used to log a human in: exchanges the callback code for
 * an access token and reads the authenticated user's profile from {@code /me}.
 */
@Component
public class OsuOAuthClient {

    private final WebClient webClient;
    private final AuctionProperties.Osu osu;

    public OsuOAuthClient(WebClient.Builder builder, AuctionProperties properties) {
        this.webClient = builder.build();
        this.osu = properties.getOsu();
    }

    public Mono<String> exchangeCode(String code) {
        return webClient.post()
                .uri(osu.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("client_id", osu.getClientId())
                        .with("client_secret", osu.getClientSecret())
                        .with("grant_type", "authorization_code")
                        .with("redirect_uri", osu.getRedirectUri())
                        .with("code", code))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.get("access_token").asText());
    }

    public Mono<OsuUserProfile> fetchMe(String accessToken) {
        return webClient.get()
                .uri(osu.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toProfile);
    }

    private OsuUserProfile toProfile(JsonNode node) {
        long osuId = node.get("id").asLong();
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

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }
}
