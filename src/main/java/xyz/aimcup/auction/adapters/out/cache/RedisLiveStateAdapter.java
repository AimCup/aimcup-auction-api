package xyz.aimcup.auction.adapters.out.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.LiveAuctionState;
import xyz.aimcup.auction.domain.port.out.LiveStatePort;

import java.time.Duration;
import java.util.UUID;

/**
 * Stores the live auction snapshot in Redis. Failures are logged and swallowed so a Redis hiccup
 * never breaks an in-flight auction (the engine keeps the authoritative copy in memory).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLiveStateAdapter implements LiveStatePort {

    private static final String KEY_PREFIX = "auction:live:";
    private static final Duration TTL = Duration.ofHours(12);

    private final ReactiveRedisTemplate<String, LiveAuctionState> template;

    private static String key(UUID auctionId) {
        return KEY_PREFIX + auctionId;
    }

    @Override
    public Mono<Void> save(LiveAuctionState state) {
        return template.opsForValue().set(key(state.getAuctionId()), state, TTL)
                .doOnError(e -> log.warn("Failed to cache live state for {}: {}", state.getAuctionId(), e.toString()))
                .onErrorResume(e -> Mono.just(false))
                .then();
    }

    @Override
    public Mono<LiveAuctionState> find(UUID auctionId) {
        return template.opsForValue().get(key(auctionId))
                .onErrorResume(e -> {
                    log.warn("Failed to read live state for {}: {}", auctionId, e.toString());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> delete(UUID auctionId) {
        return template.opsForValue().delete(key(auctionId))
                .onErrorResume(e -> Mono.just(false))
                .then();
    }
}
