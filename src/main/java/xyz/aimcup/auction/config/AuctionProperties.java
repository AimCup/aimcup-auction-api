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
    @NestedConfigurationProperty
    private Reminder reminder = new Reminder();

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

    /**
     * Discord readiness-reminder cadence, anchored to each auction's start time. Nothing is sent
     * earlier than {@code leadTimeMinutes} before the start. From there, reminders go out every
     * {@code phase1IntervalMinutes} for the first {@code phase1DurationMinutes}, then every
     * {@code phase2IntervalMinutes} until the start time, and finally every
     * {@code afterStartIntervalMinutes} while the auction is still unstarted (or paused).
     *
     * <p>Defaults: start at 12:00 → from 11:00 every 15 min (×2), from 11:30 every 10 min (×3),
     * from 12:00 every 1 min.
     */
    @Data
    public static class Reminder {
        private boolean enabled = true;
        /** How often the scheduler wakes up to evaluate reminders. */
        private int tickSeconds = 60;
        /** Don't send any reminder earlier than this many minutes before the start time. */
        private int leadTimeMinutes = 60;
        /** Length of the first (slower) window, measured from {@code leadTimeMinutes} before start. */
        private int phase1DurationMinutes = 30;
        private int phase1IntervalMinutes = 15;
        /** Interval used from the end of phase 1 until the start time. */
        private int phase2IntervalMinutes = 10;
        /** Interval used once the start time has passed but the auction is still not running. */
        private int afterStartIntervalMinutes = 1;
    }
}
