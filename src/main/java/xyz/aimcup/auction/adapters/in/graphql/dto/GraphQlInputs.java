package xyz.aimcup.auction.adapters.in.graphql.dto;

/**
 * GraphQL input types. Spring GraphQL binds incoming arguments to these records by field name.
 */
public final class GraphQlInputs {

    private GraphQlInputs() {
    }

    public record CreateAuctionInput(String name, String startAt) {
    }

    public record AuctionSettingsInput(
            int startingBalance,
            int maxBid,
            int minIncrement,
            int maxBidWindowSeconds,
            int teamSizeForPercentLimit,
            int maxBidPercent,
            int maxDescriptionLength,
            int maxTeamSize
    ) {
    }

    public record StageInput(
            int biddingTimeSeconds,
            int biddingTimeAfterBidSeconds,
            int gapTimeSeconds
    ) {
    }

    public record UpdateMetaInput(
            String name,
            String banner,
            String guildId,
            String channelId,
            String startAt
    ) {
    }

    public record AddManagerInput(long osuId, String discordId) {
    }

    public record AddPlayerInput(
            long osuId,
            String description,
            String bestBeatmapUrl,
            Double bestAccuracy,
            String worstBeatmapUrl,
            Double worstAccuracy,
            Integer qualificationRank
    ) {
    }

    public record FlagCaptainInput(String playerId, String discordId) {
    }

    public record ChangeBalanceInput(String captainId, int balance) {
    }

    public record SetProxyInput(String captainId, long osuId, String discordId) {
    }
}
