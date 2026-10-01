package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One fact, as one small Markdown file. The file is the truth; nothing else is stored.
 *
 * <pre>
 * ---
 * id: spring-ai-removed
 * type: decision
 * valid_from: 2026-09-26
 * status: current              # current | superseded
 * superseded_by: other-note-id # optional
 * valid_to: 2026-09-26         # optional
 * repo: ../my-project
 * source: git:ae2f723          # git:&lt;hash&gt; or file:&lt;path inside repo&gt;
 * quote: words copied exactly from that source
 * ---
 * # Title
 * Free text.
 * </pre>
 */
record Note(
        String id,
        String type,
        LocalDate validFrom,
        LocalDate validTo,
        String status,
        String supersededBy,
        String repo,
        String source,
        String quote,
        String title,
        String body,
        Path file) {

    boolean isCurrent() {
        return !"superseded".equals(status);
    }

    /** The folder the note lives in: brain/&lt;project&gt;/note.md. */
    String project() {
        Path parent = file.getParent();
        return parent == null ? "" : parent.getFileName().toString();
    }

    /** Was this note true on {@code day}? Ends are inclusive: a fact can be born and replaced the same day. */
    boolean validOn(LocalDate day) {
        return !day.isBefore(validFrom) && (validTo == null || !day.isAfter(validTo));
    }

    static Note parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            throw new IllegalArgumentException(file + ": must start with a --- front matter block");
        }
        Map<String, String> meta = new LinkedHashMap<>();
        int i = 1;
        for (; i < lines.size() && !lines.get(i).strip().equals("---"); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String value = line.substring(colon + 1);
            int comment = value.indexOf("  #");
            meta.put(line.substring(0, colon).strip(), (comment < 0 ? value : value.substring(0, comment)).strip());
        }
        if (i >= lines.size()) {
            throw new IllegalArgumentException(file + ": front matter is never closed");
        }
        String rest = String.join("\n", lines.subList(i + 1, lines.size())).strip();
        String title = rest.lines()
                .filter(l -> l.startsWith("# "))
                .map(l -> l.substring(2).strip())
                .findFirst()
                .orElse(meta.getOrDefault("id", file.getFileName().toString()));
        return new Note(
                required(meta, "id", file),
                required(meta, "type", file),
                LocalDate.parse(required(meta, "valid_from", file)),
                meta.containsKey("valid_to") ? LocalDate.parse(meta.get("valid_to")) : null,
                meta.getOrDefault("status", "current"),
                meta.get("superseded_by"),
                required(meta, "repo", file),
                required(meta, "source", file),
                required(meta, "quote", file),
                title,
                rest,
                file);
    }

    /** Trusted notes only: anything under a _draft folder has not been reviewed and is never read. */
    static List<Note> loadAll(Path dir) throws IOException {
        return load(dir, false);
    }

    static List<Note> load(Path dir, boolean includeDrafts) throws IOException {
        List<Note> notes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> includeDrafts || dir.relativize(p).startsWith("_draft") == false)
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("README.md"))
                    .sorted()
                    .toList()) {
                notes.add(parse(file));
            }
        }
        return notes;
    }

    private static String required(Map<String, String> meta, String key, Path file) {
        String value = meta.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(file + ": missing required field '" + key + "'");
        }
        return value;
    }
}
