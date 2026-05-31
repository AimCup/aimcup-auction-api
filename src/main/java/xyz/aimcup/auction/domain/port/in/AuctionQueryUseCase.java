package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.LiveAuctionState;

import java.util.UUID;

/**
 * Inbound port for reads, including the live subscription stream.
 */
public interface AuctionQueryUseCase {

    Mono<Auction> getAuction(UUID id);

    Flux<Auction> listManagedBy(long osuId);

    Flux<Auction> listRecent(int limit);

    /** Current live snapshot (from cache when running, otherwise derived from the durable store). */
    Mono<LiveAuctionState> getLiveState(UUID auctionId);

    /**
     * Hot stream of live snapshots. Emits the current snapshot immediately on subscribe, then every
     * subsequent change.
     */
    Flux<LiveAuctionState> subscribeLiveState(UUID auctionId);
}
