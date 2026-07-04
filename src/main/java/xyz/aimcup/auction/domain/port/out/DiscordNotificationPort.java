package xyz.aimcup.auction.domain.port.out;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.BidEvent;
import xyz.aimcup.auction.domain.model.Captain;
import xyz.aimcup.auction.domain.model.Player;

import java.util.List;

/**
 * Outbound port that mirrors the live auction into a Discord channel. Implementations must be safe
 * to call even when Discord is disabled or the auction has no channel configured (no-op).
 */
public interface DiscordNotificationPort {

    Mono<Void> auctionStarted(Auction auction);

    Mono<Void> playerUp(Auction auction, Player player, int stageIndex);

    Mono<Void> bidPlaced(Auction auction, Player player, BidEvent bid);

    Mono<Void> playerSold(Auction auction, Player player, Captain winner, int price);

    Mono<Void> playerUnsold(Auction auction, Player player);

    Mono<Void> auctionPaused(Auction auction);

    Mono<Void> auctionResumed(Auction auction);

    /** The auction reached the break before the given (1-based) stage; captains must re-confirm ready. */
    Mono<Void> stageBreak(Auction auction, int nextStageNumber);

    Mono<Void> auctionFinished(Auction auction);

    /** A captain confirmed (or cleared) readiness; broadcasts the live ready counter to the channel. */
    Mono<Void> captainReadyChanged(Auction auction, Captain captain, boolean ready, int readyCount, int totalCaptains);

    /** Periodic reminder that pings the captains who have not confirmed readiness yet. */
    Mono<Void> remindNotReady(Auction auction, List<Captain> notReadyCaptains);
}
