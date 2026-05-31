package xyz.aimcup.auction.domain.port.in.command;

/**
 * Result of attempting a bid. Rejected bids carry a human-readable {@code message} explaining why
 * (insufficient funds, below minimum increment, percentage cap, etc.).
 */
public record BidResult(boolean accepted, String message, int currentHighest) {

    public static BidResult accepted(int currentHighest) {
        return new BidResult(true, "Bid accepted", currentHighest);
    }

    public static BidResult rejected(String message, int currentHighest) {
        return new BidResult(false, message, currentHighest);
    }
}
