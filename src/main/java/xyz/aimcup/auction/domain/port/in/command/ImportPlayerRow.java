package xyz.aimcup.auction.domain.port.in.command;

/** One raw row from a CSV import (username, osuId, description). */
public record ImportPlayerRow(String username, String osuId, String description) {
}
