package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionState;

import java.util.UUID;

/**
 * Outbound port to the durable auction store (MongoDB).
 */
public interface AuctionRepositoryPort {

    Mono<Auction> findById(UUID id);

    /** Auctions the given osu! user can manage (creator or listed manager). */
    Flux<Auction> findManagedBy(long osuId);

    /** Most recently created auctions, for a public listing. */
    Flux<Auction> findRecent(int limit);

    /** All auctions currently in the given state (used by the readiness reminder loop). */
    Flux<Auction> findByState(AuctionState state);

    /** Auctions wired to the given Discord channel (used to relay channel messages to the overlay). */
    Flux<Auction> findByChannelId(String channelId);

    Mono<Auction> save(Auction auction);

    Mono<Void> deleteById(UUID id);
}
