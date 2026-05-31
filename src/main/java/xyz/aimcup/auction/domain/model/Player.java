package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A tournament participant registered to an auction. Players flagged as captains ({@code captain == true})
 * are never auctioned themselves — instead they bid on the others.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    private UUID id;
    private Long osuId;
    private String username;
    private String avatarUrl;
    private String bannerUrl;
    private String countryCode;
    private Long globalRank;
    private Long countryRank;
    private String description;

    /** When true this player is a captain and is excluded from the auction pool. */
    @Builder.Default
    private boolean captain = false;

    /** Discord snowflake of the captain (only set when {@link #captain} is true). */
    private String discordId;

    @Builder.Default
    private PlayerStatus status = PlayerStatus.AVAILABLE;

    /** Captain id that won this player (null until sold). */
    private UUID soldToCaptainId;

    /** Final winning price (null until sold). */
    private Integer soldPrice;

    // ---- qualification stats (shown while the player is up for bidding) --------------------

    /** Final qualifier ranking the player achieved (null when not provided). */
    private Integer qualificationRank;

    /** Title of the beatmap of the player's best qualifier score. */
    private String bestMapName;
    /** Cover image of the beatmap of the player's best qualifier score. */
    private String bestMapImage;
    /** Accuracy (percent) the player scored on their best qualifier map. */
    private Double bestMapAccuracy;

    /** Title of the beatmap of the player's worst qualifier score. */
    private String worstMapName;
    /** Cover image of the beatmap of the player's worst qualifier score. */
    private String worstMapImage;
    /** Accuracy (percent) the player scored on their worst qualifier map. */
    private Double worstMapAccuracy;

    private Instant addedAt;
}
