package xyz.aimcup.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Strongly typed configuration for the auction service. Bound from the {@code auction.*} tree.
 */
@Data
@ConfigurationProperties(prefix = "auction")
public class AuctionProperties {

    private String frontendUrl = "http://localhost:3000";
    private List<String> corsAllowedOrigins = List.of("http://localhost:3000");

    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();
    @NestedConfigurationProperty
    private Security security = new Security();
    @NestedConfigurationProperty
    private Osu osu = new Osu();
    @NestedConfigurationProperty
    private Discord discord = new Discord();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86_400_000L;
    }

    @Data
    public static class Security {
        /** Local dev convenience: treat every authenticated user as ROLE_ADMIN. */
        private boolean devGrantAdmin = false;
        /** osu! ids explicitly allowed to create auctions (ROLE_ADMIN). */
        private List<Long> adminOsuIds = List.of();
    }

    @Data
    public static class Osu {
        private String clientId;
        private String clientSecret;
        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        private String redirectUri;
        private String scopes = "identify public";
        @NestedConfigurationProperty
        private Api api = new Api();

        @Data
        public static class Api {
            private String clientId;
            private String clientSecret;
            private String baseUrl = "https://osu.ppy.sh/api/v2";
        }
    }

    @Data
    public static class Discord {
        private boolean enabled = false;
        private String botToken;
        /**
         * Enable the privileged MESSAGE_CONTENT gateway intent so channel messages can be relayed to
         * the stream overlay. Must also be toggled on for the bot in the Discord developer portal;
         * set to {@code false} if the bot is not authorised for it (otherwise login is rejected).
         */
        private boolean messageContentIntent = true;
    }
}
