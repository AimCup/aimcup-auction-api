package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A bidder in the auction. Mirrors the underlying {@link Player} (referenced by {@link #playerId})
 * but additionally tracks the running balance and the roster won so far.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Captain {

    private UUID id;
    /** Id of the {@link Player} this captain corresponds to. */
    private UUID playerId;
    private Long osuId;
    private String username;
    private String avatarUrl;
    private String countryCode;
    /** Discord snowflake used to authorise {@code /bid} from Discord. */
    private String discordId;

    /** Remaining credits. */
    private int balance;

    /**
     * Whether this captain has confirmed readiness. Required before an auction starts (and re-asked
     * after a pause). Managers may start/resume even if not every captain is ready.
     */
    @Builder.Default
    private boolean ready = false;

    /** Ids of the players this captain has won. */
    @Builder.Default
    private List<UUID> teamPlayerIds = new ArrayList<>();

    public int teamSize() {
        return teamPlayerIds == null ? 0 : teamPlayerIds.size();
    }
}
