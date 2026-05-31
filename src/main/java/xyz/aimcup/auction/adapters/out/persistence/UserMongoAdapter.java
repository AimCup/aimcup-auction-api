package xyz.aimcup.auction.adapters.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.User;
import xyz.aimcup.auction.domain.port.out.UserRepositoryPort;

/**
 * MongoDB persistence adapter for logged-in users (one document per osu! account).
 */
@Component
@RequiredArgsConstructor
public class UserMongoAdapter implements UserRepositoryPort {

    static final String COLLECTION = "users";
    private final ReactiveMongoTemplate template;

    @Override
    public Mono<User> findByOsuId(long osuId) {
        return template.findOne(Query.query(Criteria.where("osuId").is(osuId)), User.class, COLLECTION);
    }

    @Override
    public Mono<User> save(User user) {
        return template.save(user, COLLECTION);
    }
}
