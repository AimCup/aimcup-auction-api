package xyz.aimcup.auction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import xyz.aimcup.auction.domain.model.LiveAuctionState;

/**
 * Reactive Redis template used to cache the live auction snapshot for fast reads during a running
 * auction (the durable MongoDB store is only touched when a player's bidding completes).
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, LiveAuctionState> liveStateRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<LiveAuctionState> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, LiveAuctionState.class);

        RedisSerializationContext<String, LiveAuctionState> context = RedisSerializationContext
                .<String, LiveAuctionState>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
