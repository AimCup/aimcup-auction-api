package xyz.aimcup.auction.adapters.in.graphql.dto;

import xyz.aimcup.auction.domain.model.AuctionPhase;
import xyz.aimcup.auction.domain.model.AuctionState;
import xyz.aimcup.auction.domain.model.PlayerStatus;

import java.util.List;

/**
 * GraphQL response types. They mirror the schema exactly (ids and timestamps are strings, big
 * numbers are doubles) and are produced from the domain by {@code GraphQlMapper}. Spring GraphQL
 * resolves each field from the matching record accessor.
 */
public final class GraphQlDtos {

    private GraphQlDtos() {
    }

    public record AuctionDto(
            String id,
            String name,
            String banner,
            long creatorOsuId,
            AuctionState state,
            String startAt,
            String createdAt,
            String startedAt,
            String finishedAt,
            String guildId,
            String channelId,
            SettingsDto settings,
            List<StageDto> stages,
            List<ManagerDto> managers,
            List<PlayerDto> players,
            List<CaptainDto> captains,
            int currentStageIndex
    ) {
    }

    public record SettingsDto(
            int startingBalance,
            int maxBid,
            int minIncrement,
            int teamSizeForPercentLimit,
            int maxBidPercent,
            int maxDescriptionLength
    ) {
    }

    public record StageDto(
            int index,
            int biddingTimeSeconds,
            int biddingTimeAfterBidSeconds,
            int gapTimeSeconds
    ) {
    }

    public record ManagerDto(
            String id,
            long osuId,
            String username,
            String avatarUrl,
            String countryCode,
            String discordId,
            boolean owner,
            String addedAt
    ) {
    }

    public record PlayerDto(
            String id,
            long osuId,
            String username,
            String avatarUrl,
            String bannerUrl,
            String countryCode,
            Long globalRank,
            Long countryRank,
            String description,
            boolean captain,
            String discordId,
            PlayerStatus status,
            String soldToCaptainId,
            Integer soldPrice,
            Integer qualificationRank,
            String bestMapName,
            String bestMapImage,
            Double bestMapAccuracy,
            String worstMapName,
            String worstMapImage,
            Double worstMapAccuracy
    ) {
    }

    public record CaptainDto(
            String id,
            String playerId,
            long osuId,
            String username,
            String avatarUrl,
            String countryCode,
            String discordId,
            int balance,
            boolean ready,
            List<String> teamPlayerIds
    ) {
    }

    public record BidEventDto(
            String captainId,
            String captainUsername,
            String captainAvatarUrl,
            int amount,
            String at,
            boolean maxBid,
            String source
    ) {
    }

    public record LiveAuctionStateDto(
            String auctionId,
            AuctionState state,
            AuctionPhase phase,
            int stageIndex,
            int totalStages,
            PlayerDto currentPlayer,
            int highestBid,
            String highestBidderId,
            String highestBidderUsername,
            List<BidEventDto> bidHistory,
            double phaseEndsAtEpochMs,
            boolean pausedByOrganizer,
            String message,
            List<CaptainDto> captains,
            List<PlayerDto> players,
            List<Long> onlineOsuIds,
            int remainingCount,
            int soldCount,
            int unsoldCount,
            double version
    ) {
    }

    public record UserDto(
            String id,
            long osuId,
            String username,
            String avatarUrl,
            String countryCode,
            Long globalRank,
            Long countryRank,
            boolean admin
    ) {
    }

    public record ImportErrorDto(int line, String osuId, String username, String reason) {
    }

    public record ImportResultDto(List<PlayerDto> imported, List<ImportErrorDto> errors) {
    }

    public record BidResultDto(boolean accepted, String message, int currentHighest) {
    }

    public record ReadyResultDto(boolean ready, int readyCount, int totalCaptains) {
    }

    public record ChatMessageDto(String author, String content, String avatarUrl, String at,
                                 ChatEmbedDto embed) {
    }

    public record ChatEmbedDto(
            String color,
            String authorName,
            String authorIcon,
            String title,
            String description,
            List<ChatEmbedFieldDto> fields,
            String thumbnail,
            String image,
            String footer,
            String footerIcon,
            String timestamp
    ) {
    }

    public record ChatEmbedFieldDto(String name, String value, boolean inline) {
    }
}
