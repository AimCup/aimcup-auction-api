package xyz.aimcup.auction.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transient live snapshot of a running auction. This is the single payload pushed to subscribers
 * and cached in Redis. The whole public page can be rendered from it. The server only emits it on
 * meaningful changes (new player, new bid, sold, pause, resume, finish); the client runs the
 * countdown locally from {@link #phaseEndsAtEpochMs}, so there is no per-second network chatter.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LiveAuctionState {

    private UUID auctionId;
    private AuctionState state;
    private AuctionPhase phase;

    private int stageIndex;
    private int totalStages;

    /** Player currently up for bids (null between players / before start / after end). */
    private Player currentPlayer;

    private int highestBid;
    private UUID highestBidderId;
    private String highestBidderUsername;

    /** Bid history for the current player only; cleared when a new player comes up. */
    @Builder.Default
    private List<BidEvent> bidHistory = new ArrayList<>();

    /** Epoch millis at which the current phase ends; 0 when no timer is running. */
    private long phaseEndsAtEpochMs;

    private boolean pausedByOrganizer;
    /** Human readable status line, e.g. "Auction paused by an organizer — resuming shortly". */
    private String message;

    @Builder.Default
    private List<Captain> captains = new ArrayList<>();

    /** osu! ids currently connected to the live stream (for the green "online" dot). */
    @Builder.Default
    private List<Long> onlineOsuIds = new ArrayList<>();

    /** Full roster with up-to-date statuses so teams and lists can render directly from this. */
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    private int remainingCount;
    private int soldCount;
    private int unsoldCount;

    /** Monotonic version to let clients drop out-of-order frames. */
    private long version;
}
