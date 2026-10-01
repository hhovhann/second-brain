package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The grounding gate: a note is only trusted if its quote really occurs in its source.
 *
 * <p>Deterministic on purpose. An instruction to a model ("quote exactly") is not
 * enforcement; a string comparison is, and it cannot be talked out of its answer.
 * Whitespace and case are normalised, because wrapped lines and reflowed text are
 * ordinary; nothing else is, so a paraphrase fails.
 */
final class Check {

    private static final Pattern GIT_HASH = Pattern.compile("[0-9a-f]{7,40}");
    private static final int MIN_QUOTE_LENGTH = 12;

    record Problem(Note note, String message) {}

    private Check() {}

    static List<Problem> run(List<Note> notes, Path projectRoot) {
        List<Problem> problems = new ArrayList<>();
        List<String> ids = notes.stream().map(Note::id).toList();
        for (Note note : notes) {
            if (ids.stream().filter(note.id()::equals).count() > 1) {
                problems.add(new Problem(note, "duplicate id"));
            }
            if (note.supersededBy() != null && !ids.contains(note.supersededBy())) {
                problems.add(new Problem(note, "superseded_by points at unknown note '" + note.supersededBy() + "'"));
            }
            if (note.validTo() != null && note.validTo().isBefore(note.validFrom())) {
                problems.add(new Problem(note, "valid_to is before valid_from"));
            }
            if ("superseded".equals(note.status()) && note.supersededBy() == null) {
                problems.add(new Problem(note, "status is superseded but superseded_by is empty"));
            }
            if (normalise(note.quote()).length() < MIN_QUOTE_LENGTH) {
                problems.add(new Problem(note, "quote is too short to prove anything"));
                continue;
            }
            try {
                String source = readSource(note, projectRoot);
                if (!normalise(source).contains(normalise(note.quote()))) {
                    problems.add(new Problem(note, "quote not found in " + note.source() + ": \"" + note.quote() + "\""));
                }
            } catch (IOException | RuntimeException | InterruptedException e) {
                problems.add(new Problem(note, "cannot read " + note.source() + ": " + e.getMessage()));
            }
        }
        return problems;
    }

    static String readSource(Note note, Path projectRoot) throws IOException, InterruptedException {
        Path repo = projectRoot.resolve(note.repo()).normalize();
        String source = note.source();
        if (source.startsWith("git:")) {
            String hash = source.substring(4);
            if (!GIT_HASH.matcher(hash).matches()) {
                throw new IllegalArgumentException("not a commit hash: " + hash);
            }
            Process process = new ProcessBuilder("git", "-C", repo.toString(), "show", "-s", "--format=%B", hash)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("git could not show " + hash);
            }
            return output;
        }
        if (source.startsWith("file:")) {
            // "file:transcript.md#t=00:12:03" points at a moment; only the path is read.
            String relative = source.substring(5);
            int fragment = relative.indexOf('#');
            Path file = repo.resolve(fragment < 0 ? relative : relative.substring(0, fragment)).normalize();
            // A path written by a model or a corpus must never leave the repository.
            if (!file.startsWith(repo)) {
                throw new IllegalArgumentException("path escapes the repository: " + source);
            }
            return Files.readString(file);
        }
        throw new IllegalArgumentException("source must start with git: or file:");
    }

    static String normalise(String text) {
        return text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
