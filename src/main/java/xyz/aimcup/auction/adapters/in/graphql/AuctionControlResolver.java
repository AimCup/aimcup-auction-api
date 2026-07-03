package xyz.aimcup.auction.adapters.in.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlDtos;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlInputs;
import xyz.aimcup.auction.adapters.in.graphql.dto.GraphQlMapper;
import xyz.aimcup.auction.domain.port.in.AuctionControlUseCase;
import xyz.aimcup.auction.domain.port.in.BiddingUseCase;
import xyz.aimcup.auction.domain.port.in.ReadinessUseCase;
import xyz.aimcup.auction.domain.port.in.command.BidActor;
import xyz.aimcup.auction.security.AuthContext;
import xyz.aimcup.auction.security.AuthenticatedUser;

import java.util.UUID;

/**
 * Live-control and bidding mutations. Bidding requires a logged-in captain (verified by the engine);
 * pause/resume/team edits require a manager.
 */
@Controller
@RequiredArgsConstructor
public class AuctionControlResolver {

    private final AuctionControlUseCase controlUseCase;
    private final BiddingUseCase biddingUseCase;
    private final ReadinessUseCase readinessUseCase;

    @MutationMapping
    public Mono<Boolean> startAuction(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.start(user.osuId(), UUID.fromString(auctionId)).thenReturn(true);
    }

    @MutationMapping
    public Mono<Boolean> pauseAuction(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.pause(user.osuId(), UUID.fromString(auctionId)).thenReturn(true);
    }

    @MutationMapping
    public Mono<Boolean> resumeAuction(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.resume(user.osuId(), UUID.fromString(auctionId)).thenReturn(true);
    }

    @MutationMapping
    public Mono<Boolean> removePlayerFromTeam(
            @Argument String auctionId,
            @Argument String playerId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.removePlayerFromTeam(user.osuId(), UUID.fromString(auctionId), UUID.fromString(playerId))
                .thenReturn(true);
    }

    @MutationMapping
    public Mono<Boolean> changeCaptainBalance(
            @Argument String auctionId,
            @Argument GraphQlInputs.ChangeBalanceInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.changeCaptainBalance(user.osuId(), UUID.fromString(auctionId),
                UUID.fromString(input.captainId()), input.balance()).thenReturn(true);
    }

    @MutationMapping
    public Mono<Boolean> removeCaptainProxy(
            @Argument String auctionId,
            @Argument String captainId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return controlUseCase.removeCaptainProxy(user.osuId(), UUID.fromString(auctionId),
                UUID.fromString(captainId)).thenReturn(true);
    }

    @MutationMapping
    public Mono<GraphQlDtos.BidResultDto> placeBid(
            @Argument String auctionId,
            @Argument int amount,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return biddingUseCase.placeBid(UUID.fromString(auctionId), BidActor.web(user.osuId()), amount, "WEB")
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.BidResultDto> placeMaxBid(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return biddingUseCase.placeMaxBid(UUID.fromString(auctionId), BidActor.web(user.osuId()), "WEB")
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.ReadyResultDto> setCaptainReady(
            @Argument String auctionId,
            @Argument boolean ready,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return readinessUseCase.setReady(UUID.fromString(auctionId), BidActor.web(user.osuId()), ready)
                .map(GraphQlMapper::toDto);
    }
}
