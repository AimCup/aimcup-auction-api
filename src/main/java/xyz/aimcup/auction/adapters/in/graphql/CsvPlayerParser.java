package xyz.aimcup.auction.adapters.in.graphql;

import xyz.aimcup.auction.domain.port.in.command.ImportPlayerRow;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV reader for player imports. Expects columns in the order
 * {@code username, osuId, description, qualificationRank, bestBeatmapUrl, bestBeatmapAccuracy,
 * worstBeatmapUrl, worstBeatmapAccuracy}; a header row (detected by the literal column names) is
 * skipped. Fields may be quoted (RFC&nbsp;4180 style) so a description can itself contain commas;
 * {@code ""} inside a quoted field is an escaped quote.
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
            if (raw.strip().isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
                String lower = raw.strip().toLowerCase();
                if (lower.startsWith("username") || lower.contains("osuid")) {
                    continue; // header row
                }
            }
            List<String> f = splitCsvLine(raw);
            rows.add(new ImportPlayerRow(
                    field(f, 0), field(f, 1), field(f, 2), field(f, 3),
                    field(f, 4), field(f, 5), field(f, 6), field(f, 7)));
        }
        return rows;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index).strip() : "";
    }

    /** Splits a single CSV line into fields, honouring double-quoted fields and {@code ""} escapes. */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
