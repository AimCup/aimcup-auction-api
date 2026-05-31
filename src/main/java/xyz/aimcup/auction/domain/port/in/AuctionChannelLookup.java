package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;

/**
 * Resolves which running auction is mirrored to a given Discord channel, so the bot can route
 * {@code /bid} and organizer commands issued in that channel to the correct auction.
 */
public interface AuctionChannelLookup {

    Mono<Auction> findActiveByChannelId(String channelId);
}
