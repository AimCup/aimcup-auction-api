package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.User;

/**
 * Outbound port to the logged-in user store.
 */
public interface UserRepositoryPort {

    Mono<User> findByOsuId(long osuId);

    Mono<User> save(User user);
}
