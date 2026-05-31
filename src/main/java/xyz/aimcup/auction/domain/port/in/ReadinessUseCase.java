package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.port.in.command.BidActor;
import xyz.aimcup.auction.domain.port.in.command.ReadyResult;

import java.util.UUID;

/**
 * Inbound port for captains confirming readiness before an auction starts (and again after a pause).
 * Used by both the GraphQL mutation (web captains) and the Discord {@code /ready} command. The
 * implementation must verify the actor is actually a captain of the auction — nobody else may do it.
 */
public interface ReadinessUseCase {

    /** Sets (or clears) the acting captain's readiness and returns the updated ready counter. */
    Mono<ReadyResult> setReady(UUID auctionId, BidActor actor, boolean ready);
}
