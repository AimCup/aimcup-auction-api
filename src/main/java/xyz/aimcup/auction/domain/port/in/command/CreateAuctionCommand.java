package xyz.aimcup.auction.domain.port.in.command;

import java.time.Instant;

/** Payload to create a new auction. */
public record CreateAuctionCommand(String name, Instant startAt) {
}
