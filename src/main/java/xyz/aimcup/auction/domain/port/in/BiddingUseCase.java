package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.port.in.command.BidActor;
import xyz.aimcup.auction.domain.port.in.command.BidResult;

import java.util.UUID;

/**
 * Inbound port for placing bids. Used by both the GraphQL mutation (web captains) and the Discord
 * {@code /bid} command. The implementation must verify the actor is actually a captain of the
 * auction — nobody else may bid.
 */
public interface BiddingUseCase {

    /**
     * Places a bid of {@code amount} credits on the player currently up for auction.
     */
    Mono<BidResult> placeBid(UUID auctionId, BidActor actor, int amount, String source);

    /**
     * Places the instant-win "max bid" configured for the auction.
     */
    Mono<BidResult> placeMaxBid(UUID auctionId, BidActor actor, String source);
}
