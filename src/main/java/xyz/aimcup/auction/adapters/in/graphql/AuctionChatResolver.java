package xyz.aimcup.auction.adapters.in.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlDtos;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlMapper;
import xyz.aimcup.auction.domain.port.in.ChatQueryUseCase;

import java.util.UUID;

/**
 * Live-feed chat for an auction. The {@code auctionChat} subscription streams the read-only feed
 * (Discord relays + bot embeds) to anyone — it is public, like the overlay. Sending messages from the
 * web has been removed; the feed mirrors the linked Discord channel only.
 */
@Controller
@RequiredArgsConstructor
public class AuctionChatResolver {

    private final ChatQueryUseCase chatQueryUseCase;

    @SubscriptionMapping
    public Flux<GraphQlDtos.ChatMessageDto> auctionChat(@Argument String auctionId) {
        return chatQueryUseCase.subscribe(UUID.fromString(auctionId)).map(GraphQlMapper::toDto);
    }
}
