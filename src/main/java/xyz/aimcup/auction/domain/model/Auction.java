package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Auction aggregate root. Pure domain object (no persistence/web annotations) so the auction
 * engine can mutate roster, balances and statuses ergonomically. Persistence is handled by a
 * separate document + mapper in the outbound adapter.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Auction {

    private UUID id;
    private String name;
    private String banner;
    /** osu! id of the admin who created the auction (the owner). */
    private Long creatorOsuId;

    @Builder.Default
    private AuctionState state = AuctionState.SCHEDULED;

    private Instant startAt;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    /** Discord server id the live auction is mirrored to. */
    private String guildId;
    /** Discord channel id the live auction is mirrored to. */
    private String channelId;

    @Builder.Default
    private AuctionSettings settings = AuctionSettings.defaults();

    @Builder.Default
    private List<AuctionStage> stages = new ArrayList<>();

    @Builder.Default
    private List<Manager> managers = new ArrayList<>();

    @Builder.Default
    private List<Player> players = new ArrayList<>();

    @Builder.Default
    private List<Captain> captains = new ArrayList<>();

    /** Index of the stage currently in progress (or last completed). */
    @Builder.Default
    private int currentStageIndex = 0;

    // ---- convenience accessors -------------------------------------------------

    public boolean isManager(long osuId) {
        return managers.stream().anyMatch(m -> m.getOsuId() != null && m.getOsuId() == osuId);
    }

    public boolean isOwner(long osuId) {
        return creatorOsuId != null && creatorOsuId == osuId;
    }

    public Optional<Player> findPlayer(UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst();
    }

    public Optional<Player> findPlayerByOsuId(long osuId) {
        return players.stream().filter(p -> p.getOsuId() != null && p.getOsuId() == osuId).findFirst();
    }

    public Optional<Captain> findCaptain(UUID captainId) {
        return captains.stream().filter(c -> c.getId().equals(captainId)).findFirst();
    }

    public Optional<Captain> findCaptainByDiscordId(String discordId) {
        if (discordId == null) {
            return Optional.empty();
        }
        return captains.stream().filter(c -> discordId.equals(c.getDiscordId())).findFirst();
    }

    public Optional<Manager> findManagerByDiscordId(String discordId) {
        if (discordId == null) {
            return Optional.empty();
        }
        return managers.stream().filter(m -> discordId.equals(m.getDiscordId())).findFirst();
    }

    /** Players eligible to be auctioned (everyone who is not a captain). */
    public List<Player> auctionablePlayers() {
        return players.stream().filter(p -> !p.isCaptain()).toList();
    }
}
