package xyz.aimcup.auction.domain.port.in.command;

import java.time.Instant;

/**
 * Editable top-level auction metadata. Null fields are left unchanged.
 */
public record UpdateMetaCommand(
        String name,
        String banner,
        String guildId,
        String channelId,
        Instant startAt
) {
}
