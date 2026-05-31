package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Flux;
import xyz.aimcup.auction.domain.model.ChatMessage;

import java.util.UUID;

/**
 * Inbound port for subscribing to the live feed of Discord messages for an auction. Open to
 * anonymous spectators (the overlay and public page are viewable by anyone).
 */
public interface ChatQueryUseCase {

    /** Hot stream: a small replay of recent messages first, then every new message. */
    Flux<ChatMessage> subscribe(UUID auctionId);
}
