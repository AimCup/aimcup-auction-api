package xyz.aimcup.auction.domain.port.in.command;

/**
 * Identifies who is bidding. Web bids carry an {@code osuId} (from the JWT); Discord bids carry a
 * {@code discordId}. The engine resolves either to a captain of the auction.
 */
public record BidActor(Long osuId, String discordId) {

    public static BidActor web(long osuId) {
        return new BidActor(osuId, null);
    }

    public static BidActor discord(String discordId) {
        return new BidActor(null, discordId);
    }
}
