package xyz.aimcup.auction.application.engine;

import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionPhase;
import xyz.aimcup.auction.domain.model.BidEvent;
import xyz.aimcup.auction.domain.model.LiveAuctionState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory runtime state for a single running auction. All fields are only ever touched from the
 * engine's single worker thread, so plain mutable fields are safe here.
 */
class RunningAuction {

    final Auction auction;
    final Sinks.Many<LiveAuctionState> sink;

    int stageIndex;
    Deque<UUID> queue = new ArrayDeque<>();

    UUID currentPlayerId;
    int highestBid;
    UUID highestBidderId;
    String highestBidderUsername;
    List<BidEvent> bidHistory = new ArrayList<>();

    /**
     * Captains who placed a max bid for the current player, in the order they joined. The winner is
     * drawn from this set when the {@link AuctionPhase#MAX_BID_WINDOW} closes. A {@link LinkedHashSet}
     * keeps it duplicate-free (one max bid per captain) while preserving join order for display.
     */
    Set<UUID> maxBidders = new LinkedHashSet<>();
    /** The captain drawn to win during {@link AuctionPhase#MAX_BID_DRAW}; null until the draw runs. */
    UUID maxBidWinnerId;

    AuctionPhase phase = AuctionPhase.WAITING_TO_START;
    long phaseEndsAtEpochMs;
    String message;

    boolean pauseRequested;

    Disposable deadline;
    long version;

    RunningAuction(Auction auction, Sinks.Many<LiveAuctionState> sink) {
        this.auction = auction;
        this.sink = sink;
    }
}
