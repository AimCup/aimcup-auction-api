package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.LiveAuctionState;

import java.util.UUID;

/**
 * Outbound port to the fast live-state cache (Redis). The running auction snapshot is written here
 * on every change so reads during an auction never touch MongoDB; the durable store is only updated
 * when a player's bidding completes.
 */
public interface LiveStatePort {

    Mono<Void> save(LiveAuctionState state);

    Mono<LiveAuctionState> find(UUID auctionId);

    Mono<Void> delete(UUID auctionId);
}
