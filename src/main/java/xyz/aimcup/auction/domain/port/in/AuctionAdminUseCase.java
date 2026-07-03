package xyz.aimcup.auction.domain.port.in;

import reactor.core.publisher.Mono;
import xyz.aimcup.auction.domain.model.Auction;
import xyz.aimcup.auction.domain.model.AuctionSettings;
import xyz.aimcup.auction.domain.model.AuctionStage;
import xyz.aimcup.auction.domain.model.Manager;
import xyz.aimcup.auction.domain.model.Player;
import xyz.aimcup.auction.domain.port.in.command.AddPlayerCommand;
import xyz.aimcup.auction.domain.port.in.command.CreateAuctionCommand;
import xyz.aimcup.auction.domain.port.in.command.ImportPlayerRow;
import xyz.aimcup.auction.domain.port.in.command.ImportPlayersResult;
import xyz.aimcup.auction.domain.port.in.command.UpdateMetaCommand;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for configuring an auction before it starts. Every method authorises the acting
 * osu! user against the auction (creator, manager or ROLE_ADMIN as appropriate).
 */
public interface AuctionAdminUseCase {

    Mono<Auction> createAuction(long actingOsuId, boolean isAdmin, CreateAuctionCommand command);

    Mono<Auction> updateSettings(long actingOsuId, UUID auctionId, AuctionSettings settings);

    Mono<Auction> updateStages(long actingOsuId, UUID auctionId, List<AuctionStage> stages);

    Mono<Auction> updateMeta(long actingOsuId, UUID auctionId, UpdateMetaCommand command);

    /** Only the owner may delete. */
    Mono<Void> deleteAuction(long actingOsuId, UUID auctionId);

    Mono<Manager> addManager(long actingOsuId, UUID auctionId, long osuId, String discordId);

    Mono<Void> removeManager(long actingOsuId, UUID auctionId, UUID managerId);

    Mono<Player> addPlayer(long actingOsuId, UUID auctionId, AddPlayerCommand command);

    Mono<ImportPlayersResult> importPlayers(long actingOsuId, UUID auctionId, List<ImportPlayerRow> rows);

    Mono<Void> removePlayer(long actingOsuId, UUID auctionId, UUID playerId);

    /** Flags an existing player as a captain (excluding them from the pool). */
    Mono<Player> setCaptain(long actingOsuId, UUID auctionId, UUID playerId, String discordId);

    Mono<Player> unsetCaptain(long actingOsuId, UUID auctionId, UUID playerId);

    /**
     * Assigns (or replaces) a proxy that acts on a captain's behalf. Organizer only, before the
     * auction starts. The proxy is identified by its own osu! id (+ optional Discord id) and must not
     * already be a player in the auction.
     */
    Mono<Auction> setCaptainProxy(long actingOsuId, UUID auctionId, UUID captainId, long proxyOsuId,
                                  String proxyDiscordId);
}
