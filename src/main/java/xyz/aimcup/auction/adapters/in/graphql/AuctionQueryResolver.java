package xyz.aimcup.auction.adapters.in.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlDtos;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlMapper;
import xyz.aimcup.auction.application.service.UserService;
import xyz.aimcup.auction.domain.port.in.AuctionQueryUseCase;
import xyz.aimcup.auction.security.AuthContext;
import xyz.aimcup.auction.security.AuthenticatedUser;

import java.util.UUID;

/**
 * Read-side GraphQL queries. Viewing auctions and live state is public; {@code me} and
 * {@code myAuctions} require authentication.
 */
@Controller
@RequiredArgsConstructor
public class AuctionQueryResolver {

    private final AuctionQueryUseCase queryUseCase;
    private final UserService userService;

    @QueryMapping
    public Mono<GraphQlDtos.UserDto> me(@ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        if (principal == null) {
            return Mono.empty();
        }
        return userService.findByOsuId(principal.osuId())
                .map(user -> GraphQlMapper.toDto(user, principal.admin()))
                .defaultIfEmpty(new GraphQlDtos.UserDto(null, principal.osuId(), principal.username(),
                        principal.avatarUrl(), null, null, null, principal.admin()));
    }

    @QueryMapping
    public Mono<GraphQlDtos.AuctionDto> auction(@Argument String id) {
        return queryUseCase.getAuction(UUID.fromString(id)).map(GraphQlMapper::toDto);
    }

    @QueryMapping
    public Flux<GraphQlDtos.AuctionDto> myAuctions(
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return queryUseCase.listManagedBy(user.osuId()).map(GraphQlMapper::toDto);
    }

    @QueryMapping
    public Flux<GraphQlDtos.AuctionDto> recentAuctions(@Argument Integer limit) {
        return queryUseCase.listRecent(limit == null ? 20 : Math.min(limit, 100)).map(GraphQlMapper::toDto);
    }

    @QueryMapping
    public Mono<GraphQlDtos.LiveAuctionStateDto> liveState(@Argument String auctionId) {
        return queryUseCase.getLiveState(UUID.fromString(auctionId)).map(GraphQlMapper::toDto);
    }
}
