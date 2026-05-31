package xyz.aimcup.auction.adapters.in.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlDtos;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlMapper;
import xyz.aimcup.auction.domain.port.in.AuctionQueryUseCase;
import xyz.aimcup.auction.security.AuthContext;
import xyz.aimcup.auction.security.AuthenticatedUser;
import xyz.aimcup.auction.security.PresenceTracker;

import java.util.UUID;

/**
 * Live auction subscription. Open to anonymous spectators (a public auction page is viewable by
 * anyone); authenticated subscribers are additionally registered with the {@link PresenceTracker}
 * so the UI can show a green dot next to captains who are present.
 */
@Controller
@RequiredArgsConstructor
public class LiveAuctionSubscriptionResolver {

    private final AuctionQueryUseCase queryUseCase;
    private final PresenceTracker presence;

    @SubscriptionMapping
    public Flux<GraphQlDtos.LiveAuctionStateDto> liveAuction(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        UUID id = UUID.fromString(auctionId);
        Flux<GraphQlDtos.LiveAuctionStateDto> stream =
                queryUseCase.subscribeLiveState(id).map(GraphQlMapper::toDto);
        if (principal != null) {
            stream = stream
                    .doOnSubscribe(s -> presence.join(id, principal.osuId()))
                    .doFinally(signal -> presence.leave(id, principal.osuId()));
        }
        return stream;
    }
}
