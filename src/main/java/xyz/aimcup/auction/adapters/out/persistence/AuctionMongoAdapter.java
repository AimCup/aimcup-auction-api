package xyz.aimcup.auction.adapters.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionState;
import xyz.aimcup.auction.domain.port.out.AuctionRepositoryPort;

import java.util.UUID;

/**
 * MongoDB persistence adapter. Works directly with the pure-domain {@link Auction} aggregate:
 * Spring Data treats the {@code id} property as {@code _id} by convention, so no annotations are
 * needed on the domain and the hexagonal boundary stays clean.
 */
@Component
@RequiredArgsConstructor
public class AuctionMongoAdapter implements AuctionRepositoryPort {

    static final String COLLECTION = "auctions";
    private final ReactiveMongoTemplate template;

    @Override
    public Mono<Auction> findById(UUID id) {
        return template.findById(id, Auction.class, COLLECTION);
    }

    @Override
    public Flux<Auction> findManagedBy(long osuId) {
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("creatorOsuId").is(osuId),
                Criteria.where("managers.osuId").is(osuId)
        )).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return template.find(query, Auction.class, COLLECTION);
    }

    @Override
    public Flux<Auction> findRecent(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        return template.find(query, Auction.class, COLLECTION);
    }

    @Override
    public Flux<Auction> findByState(AuctionState state) {
        return template.find(Query.query(Criteria.where("state").is(state)), Auction.class, COLLECTION);
    }

    @Override
    public Flux<Auction> findByChannelId(String channelId) {
        return template.find(Query.query(Criteria.where("channelId").is(channelId)), Auction.class, COLLECTION);
    }

    @Override
    public Mono<Auction> save(Auction auction) {
        return template.save(auction, COLLECTION);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return template.remove(Query.query(Criteria.where("_id").is(id)), COLLECTION).then();
    }
}
