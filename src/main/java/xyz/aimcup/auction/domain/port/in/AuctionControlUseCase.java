package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;

import java.util.UUID;

/**
 * Inbound port for live control of a running auction. All operations require the acting user to be
 * a manager of the auction.
 */
public interface AuctionControlUseCase {

    /** Begins the auction (requires at least one captain). */
    Mono<Void> start(long actingOsuId, UUID auctionId);

    /**
     * Requests a pause: the current player's bidding finishes normally, then the auction halts
     * before the next player.
     */
    Mono<Void> pause(long actingOsuId, UUID auctionId);

    /** Resumes a paused auction; the next gap is doubled once. */
    Mono<Void> resume(long actingOsuId, UUID auctionId);

    /**
     * Removes an already-sold player from their team and refunds the price to the captain.
     * Only allowed while the auction is paused.
     */
    Mono<Void> removePlayerFromTeam(long actingOsuId, UUID auctionId, UUID playerId);

    /** Overrides a captain's balance. */
    Mono<Void> changeCaptainBalance(long actingOsuId, UUID auctionId, UUID captainId, int newBalance);

    /**
     * Assigns (or replaces) the proxy that acts on a captain's behalf. Organizer only, and only while
     * the auction is scheduled (before start) or paused. The proxy is identified by its own osu! id
     * (+ optional Discord id) and must not already be a player or another captain's proxy.
     */
    Mono<Auction> setCaptainProxy(long actingOsuId, UUID auctionId, UUID captainId, long proxyOsuId,
                                  String proxyDiscordId);

    /**
     * Revokes a captain's proxy. Organizer only. While the auction is running this is allowed only
     * when it is paused; before it starts (scheduled) it is a normal edit.
     */
    Mono<Void> removeCaptainProxy(long actingOsuId, UUID auctionId, UUID captainId);
}
