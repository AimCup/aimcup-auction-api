package xyz.aimcup.auction.domain.port.in;

import xyz.aimcup.auction.domain.model.ChatMessage;

import java.util.UUID;

/**
 * Inbound port used by the Discord adapter to relay a channel message into an auction's live feed.
 */
public interface ChatRelayUseCase {

    void publish(UUID auctionId, ChatMessage message);
}
