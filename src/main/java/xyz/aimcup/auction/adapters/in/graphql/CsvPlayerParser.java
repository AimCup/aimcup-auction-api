package xyz.aimcup.auction.adapters.in.graphql;

import xyz.aimcup.auction.domain.port.in.command.ImportPlayerRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV reader for player imports. Expects columns in the order {@code username, osuId,
 * description}; a header row (detected by the literal column names) is skipped. The description is
 * the remainder of the line so it may itself contain commas.
 */
public final class CsvPlayerParser {

    private CsvPlayerParser() {
    }

    public static List<ImportPlayerRow> parse(String csv) {
        List<ImportPlayerRow> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        boolean first = true;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
                String lower = line.toLowerCase();
                if (lower.contains("osuid") || lower.startsWith("username")) {
                    continue; // header row
                }
            }
            String[] parts = line.split(",", 3);
            String username = parts.length > 0 ? unquote(parts[0]) : null;
            String osuId = parts.length > 1 ? unquote(parts[1]) : null;
            String description = parts.length > 2 ? unquote(parts[2]) : "";
            rows.add(new ImportPlayerRow(username, osuId, description));
        }
        return rows;
    }

    private static String unquote(String value) {
        String trimmed = value.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed;
    }
}
