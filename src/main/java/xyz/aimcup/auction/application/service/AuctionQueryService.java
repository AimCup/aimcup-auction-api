package xyz.aimcup.auction.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.application.engine.AuctionEngine;
import xyz.aimcup.auction.domain.exception.NotFoundException;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.LiveAuctionState;
import xyz.aimcup.auction.domain.port.in.AuctionQueryUseCase;
import xyz.aimcup.auction.domain.port.out.AuctionRepositoryPort;

import java.util.UUID;

/**
 * Read side. Durable lookups go to MongoDB; the live snapshot and subscription stream are served by
 * the {@link AuctionEngine}.
 */
@Service
@RequiredArgsConstructor
public class AuctionQueryService implements AuctionQueryUseCase {

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionEngine engine;

    @Override
    public Mono<Auction> getAuction(UUID id) {
        return auctionRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Auction not found")));
    }

    @Override
    public Flux<Auction> listManagedBy(long osuId) {
        return auctionRepository.findManagedBy(osuId);
    }

    @Override
    public Flux<Auction> listRecent(int limit) {
        return auctionRepository.findRecent(limit);
    }

    @Override
    public Mono<LiveAuctionState> getLiveState(UUID auctionId) {
        return engine.liveSnapshot(auctionId);
    }

    @Override
    public Flux<LiveAuctionState> subscribeLiveState(UUID auctionId) {
        return engine.liveStream(auctionId);
    }
}
