package xyz.aimcup.auction.application.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import xyz.aimcup.auction.domain.model.ChatMessage;
import xyz.aimcup.auction.domain.port.in.ChatQueryUseCase;
import xyz.aimcup.auction.domain.port.in.ChatRelayUseCase;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fan-out of Discord channel messages to overlay/web subscribers, keyed by auction. Each
 * auction has a replay sink so a freshly connected overlay immediately sees the most recent messages.
 */
@Service
public class ChatService implements ChatRelayUseCase, ChatQueryUseCase {

    private static final int REPLAY = 30;

    private final Map<UUID, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();

    private Sinks.Many<ChatMessage> sinkFor(UUID auctionId) {
        return sinks.computeIfAbsent(auctionId, id -> Sinks.many().replay().limit(REPLAY));
    }

    @Override
    public void publish(UUID auctionId, ChatMessage message) {
        // The Discord gateway relay can deliver messages from several gateway threads concurrently, so
        // a plain tryEmitNext could hit FAIL_NON_SERIALIZED and silently drop a message. Busy-loop
        // briefly on contention instead; it resolves in microseconds, the timeout is only a backstop.
        sinkFor(auctionId).emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
    }

    @Override
    public Flux<ChatMessage> subscribe(UUID auctionId) {
        return sinkFor(auctionId).asFlux();
    }
}
