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
import xyz.aimcup.auction.domain.model.AuctionSettings;
import xyz.aimcup.auction.domain.model.AuctionStage;
import xyz.aimcup.auction.domain.port.in.AuctionAdminUseCase;
import xyz.aimcup.auction.domain.port.in.command.AddPlayerCommand;
import xyz.aimcup.auction.domain.port.in.command.CreateAuctionCommand;
import xyz.aimcup.auction.domain.port.in.command.UpdateMetaCommand;
import xyz.aimcup.auction.security.AuthContext;
import xyz.aimcup.auction.security.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

/**
 * Mutations for configuring an auction before it runs: creation, settings, stages, managers,
 * players, CSV import and captain flagging.
 */
@Controller
@RequiredArgsConstructor
public class AuctionAdminResolver {

    private final AuctionAdminUseCase adminUseCase;

    @MutationMapping
    public Mono<GraphQlDtos.AuctionDto> createAuction(
            @Argument GraphQlInputs.CreateAuctionInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        CreateAuctionCommand command = new CreateAuctionCommand(input.name(),
                GraphQlMapper.parseInstant(input.startAt()));
        return adminUseCase.createAuction(user.osuId(), user.admin() || user.hasRole("ROLE_ADMIN"), command)
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.AuctionDto> updateAuctionSettings(
            @Argument String auctionId,
            @Argument GraphQlInputs.AuctionSettingsInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        AuctionSettings settings = AuctionSettings.builder()
                .startingBalance(input.startingBalance())
                .maxBid(input.maxBid())
                .minIncrement(input.minIncrement())
                .maxBidWindowSeconds(input.maxBidWindowSeconds())
                .teamSizeForPercentLimit(input.teamSizeForPercentLimit())
                .maxBidPercent(input.maxBidPercent())
                .maxDescriptionLength(input.maxDescriptionLength())
                .maxTeamSize(input.maxTeamSize())
                .build();
        return adminUseCase.updateSettings(user.osuId(), UUID.fromString(auctionId), settings)
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.AuctionDto> updateAuctionStages(
            @Argument String auctionId,
            @Argument List<GraphQlInputs.StageInput> stages,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        List<AuctionStage> domainStages = stages.stream()
                .map(s -> AuctionStage.builder()
                        .biddingTimeSeconds(s.biddingTimeSeconds())
                        .biddingTimeAfterBidSeconds(s.biddingTimeAfterBidSeconds())
                        .gapTimeSeconds(s.gapTimeSeconds())
                        .build())
                .toList();
        return adminUseCase.updateStages(user.osuId(), UUID.fromString(auctionId), domainStages)
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.AuctionDto> updateAuctionMeta(
            @Argument String auctionId,
            @Argument GraphQlInputs.UpdateMetaInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        UpdateMetaCommand command = new UpdateMetaCommand(input.name(), input.banner(),
                input.guildId(), input.channelId(), GraphQlMapper.parseInstant(input.startAt()));
        return adminUseCase.updateMeta(user.osuId(), UUID.fromString(auctionId), command)
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<Boolean> deleteAuction(
            @Argument String auctionId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.deleteAuction(user.osuId(), UUID.fromString(auctionId)).thenReturn(true);
    }

    @MutationMapping
    public Mono<GraphQlDtos.ManagerDto> addManager(
            @Argument String auctionId,
            @Argument GraphQlInputs.AddManagerInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.addManager(user.osuId(), UUID.fromString(auctionId), input.osuId(), input.discordId())
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<Boolean> removeManager(
            @Argument String auctionId,
            @Argument String managerId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.removeManager(user.osuId(), UUID.fromString(auctionId), UUID.fromString(managerId))
                .thenReturn(true);
    }

    @MutationMapping
    public Mono<GraphQlDtos.PlayerDto> addPlayer(
            @Argument String auctionId,
            @Argument GraphQlInputs.AddPlayerInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        AddPlayerCommand command = new AddPlayerCommand(
                input.osuId(), input.description(),
                input.bestBeatmapUrl(), input.bestAccuracy(),
                input.worstBeatmapUrl(), input.worstAccuracy(),
                input.qualificationRank());
        return adminUseCase.addPlayer(user.osuId(), UUID.fromString(auctionId), command)
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.ImportResultDto> importPlayers(
            @Argument String auctionId,
            @Argument String csv,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.importPlayers(user.osuId(), UUID.fromString(auctionId), CsvPlayerParser.parse(csv))
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<Boolean> removePlayer(
            @Argument String auctionId,
            @Argument String playerId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.removePlayer(user.osuId(), UUID.fromString(auctionId), UUID.fromString(playerId))
                .thenReturn(true);
    }

    @MutationMapping
    public Mono<GraphQlDtos.PlayerDto> setCaptain(
            @Argument String auctionId,
            @Argument GraphQlInputs.FlagCaptainInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.setCaptain(user.osuId(), UUID.fromString(auctionId),
                        UUID.fromString(input.playerId()), input.discordId())
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.PlayerDto> unsetCaptain(
            @Argument String auctionId,
            @Argument String playerId,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.unsetCaptain(user.osuId(), UUID.fromString(auctionId), UUID.fromString(playerId))
                .map(GraphQlMapper::toDto);
    }

    @MutationMapping
    public Mono<GraphQlDtos.AuctionDto> setCaptainProxy(
            @Argument String auctionId,
            @Argument GraphQlInputs.SetProxyInput input,
            @ContextValue(name = AuthContext.KEY, required = false) AuthenticatedUser principal) {
        AuthenticatedUser user = AuthContext.require(principal);
        return adminUseCase.setCaptainProxy(user.osuId(), UUID.fromString(auctionId),
                        UUID.fromString(input.captainId()), input.osuId(), input.discordId())
                .map(GraphQlMapper::toDto);
    }
}
