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
 * Streams the Discord channel messages relayed for an auction to the stream overlay. Public, like
 * the live-auction subscription.
 */
@Controller
@RequiredArgsConstructor
public class AuctionChatSubscriptionResolver {

    private final ChatQueryUseCase chatQueryUseCase;

    @SubscriptionMapping
    public Flux<GraphQlDtos.ChatMessageDto> auctionChat(@Argument String auctionId) {
        return chatQueryUseCase.subscribe(UUID.fromString(auctionId)).map(GraphQlMapper::toDto);
    }
}
