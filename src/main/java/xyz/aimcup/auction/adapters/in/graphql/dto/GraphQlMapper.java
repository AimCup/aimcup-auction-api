package xyz.aimcup.auction.adapters.in.graphql.dto;

import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionSettings;
import xyz.aimcup.auction.domain.model.AuctionStage;
import xyz.aimcup.auction.domain.model.BidEvent;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.ChatMessage;
import xyz.aimcup.auction.domain.model.LiveAuctionState;
import xyz.aimcup.auction.domain.model.Manager;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.model.User;
import xyz.aimcup.auction.domain.port.in.command.BidResult;
import xyz.aimcup.auction.domain.port.in.command.ImportPlayersResult;
import xyz.aimcup.auction.domain.port.in.command.ReadyResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Converts domain objects to GraphQL DTOs (and parses the few string inputs back to domain types).
 */
public final class GraphQlMapper {

    private GraphQlMapper() {
    }

    // ---- inbound parsing -------------------------------------------------------------------

    /** Accepts both {@code 2026-06-01T18:00:00Z} and offset forms; returns null for blank input. */
    public static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value.trim()).toInstant();
        }
    }

    // ---- outbound mapping ------------------------------------------------------------------

    public static GraphQlDtos.AuctionDto toDto(Auction a) {
        return new GraphQlDtos.AuctionDto(
                id(a.getId()),
                a.getName(),
                a.getBanner(),
                a.getCreatorOsuId() == null ? 0 : a.getCreatorOsuId(),
                a.getState(),
                iso(a.getStartAt()),
                iso(a.getCreatedAt()),
                iso(a.getStartedAt()),
                iso(a.getFinishedAt()),
                a.getGuildId(),
                a.getChannelId(),
                toDto(a.getSettings()),
                a.getStages().stream().map(GraphQlMapper::toDto).toList(),
                a.getManagers().stream().map(GraphQlMapper::toDto).toList(),
                a.getPlayers().stream().map(GraphQlMapper::toDto).toList(),
                a.getCaptains().stream().map(GraphQlMapper::toDto).toList(),
                a.getCurrentStageIndex());
    }

    public static GraphQlDtos.SettingsDto toDto(AuctionSettings s) {
        return new GraphQlDtos.SettingsDto(
                s.getStartingBalance(), s.getMaxBid(), s.getMinIncrement(),
                s.getTeamSizeForPercentLimit(), s.getMaxBidPercent(), s.getMaxDescriptionLength());
    }

    public static GraphQlDtos.StageDto toDto(AuctionStage s) {
        return new GraphQlDtos.StageDto(
                s.getIndex(), s.getBiddingTimeSeconds(), s.getBiddingTimeAfterBidSeconds(), s.getGapTimeSeconds());
    }

    public static GraphQlDtos.ManagerDto toDto(Manager m) {
        return new GraphQlDtos.ManagerDto(
                id(m.getId()), nz(m.getOsuId()), m.getUsername(), m.getAvatarUrl(),
                m.getCountryCode(), m.getDiscordId(), m.isOwner(), iso(m.getAddedAt()));
    }

    public static GraphQlDtos.PlayerDto toDto(Player p) {
        return new GraphQlDtos.PlayerDto(
                id(p.getId()), nz(p.getOsuId()), p.getUsername(), p.getAvatarUrl(), p.getBannerUrl(),
                p.getCountryCode(), p.getGlobalRank(), p.getCountryRank(), p.getDescription(),
                p.isCaptain(), p.getDiscordId(), p.getStatus(), id(p.getSoldToCaptainId()), p.getSoldPrice(),
                p.getQualificationRank(),
                p.getBestMapName(), p.getBestMapImage(), p.getBestMapAccuracy(),
                p.getWorstMapName(), p.getWorstMapImage(), p.getWorstMapAccuracy());
    }

    public static GraphQlDtos.CaptainDto toDto(Captain c) {
        return new GraphQlDtos.CaptainDto(
                id(c.getId()), id(c.getPlayerId()), nz(c.getOsuId()), c.getUsername(), c.getAvatarUrl(),
                c.getCountryCode(), c.getDiscordId(), c.getBalance(), c.isReady(),
                c.getTeamPlayerIds() == null ? List.of() : c.getTeamPlayerIds().stream().map(GraphQlMapper::id).toList());
    }

    public static GraphQlDtos.BidEventDto toDto(BidEvent b) {
        return new GraphQlDtos.BidEventDto(
                id(b.getCaptainId()), b.getCaptainUsername(), b.getCaptainAvatarUrl(),
                b.getAmount(), iso(b.getAt()), b.isMaxBid(), b.getSource());
    }

    public static GraphQlDtos.LiveAuctionStateDto toDto(LiveAuctionState s) {
        return new GraphQlDtos.LiveAuctionStateDto(
                id(s.getAuctionId()),
                s.getState(),
                s.getPhase(),
                s.getStageIndex(),
                s.getTotalStages(),
                s.getCurrentPlayer() == null ? null : toDto(s.getCurrentPlayer()),
                s.getHighestBid(),
                id(s.getHighestBidderId()),
                s.getHighestBidderUsername(),
                s.getBidHistory().stream().map(GraphQlMapper::toDto).toList(),
                s.getPhaseEndsAtEpochMs(),
                s.isPausedByOrganizer(),
                s.getMessage(),
                s.getCaptains().stream().map(GraphQlMapper::toDto).toList(),
                s.getPlayers().stream().map(GraphQlMapper::toDto).toList(),
                s.getOnlineOsuIds(),
                s.getRemainingCount(),
                s.getSoldCount(),
                s.getUnsoldCount(),
                s.getVersion());
    }

    public static GraphQlDtos.UserDto toDto(User u, boolean admin) {
        return new GraphQlDtos.UserDto(
                id(u.getId()), nz(u.getOsuId()), u.getUsername(), u.getAvatarUrl(),
                u.getCountryCode(), u.getGlobalRank(), u.getCountryRank(), admin);
    }

    public static GraphQlDtos.ImportResultDto toDto(ImportPlayersResult result) {
        return new GraphQlDtos.ImportResultDto(
                result.imported().stream().map(GraphQlMapper::toDto).toList(),
                result.errors().stream()
                        .map(e -> new GraphQlDtos.ImportErrorDto(e.line(), e.osuId(), e.username(), e.reason()))
                        .toList());
    }

    public static GraphQlDtos.BidResultDto toDto(BidResult r) {
        return new GraphQlDtos.BidResultDto(r.accepted(), r.message(), r.currentHighest());
    }

    public static GraphQlDtos.ReadyResultDto toDto(ReadyResult r) {
        return new GraphQlDtos.ReadyResultDto(r.ready(), r.readyCount(), r.totalCaptains());
    }

    public static GraphQlDtos.ChatMessageDto toDto(ChatMessage m) {
        return new GraphQlDtos.ChatMessageDto(m.author(), m.content(), m.avatarUrl(), iso(m.at()),
                m.embed() == null ? null : toDto(m.embed()));
    }

    public static GraphQlDtos.ChatEmbedDto toDto(ChatMessage.ChatEmbed e) {
        return new GraphQlDtos.ChatEmbedDto(
                e.color(), e.authorName(), e.authorIcon(), e.title(), e.description(),
                e.fields() == null ? List.of()
                        : e.fields().stream().map(GraphQlMapper::toDto).toList(),
                e.thumbnail(), e.image(), e.footer(), e.footerIcon(), iso(e.timestamp()));
    }

    public static GraphQlDtos.ChatEmbedFieldDto toDto(ChatMessage.ChatEmbedField f) {
        return new GraphQlDtos.ChatEmbedFieldDto(f.name(), f.value(), f.inline());
    }

    // ---- primitives ------------------------------------------------------------------------

    private static String id(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
