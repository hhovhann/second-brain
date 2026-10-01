package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one step a person does by hand: read the drafts, then accept them.
 *
 * <p>{@code brain review} lists topics waiting; {@code brain review <topic>} shows every draft
 * with its quote and whether the quote was found, and only then asks. Accepting moves the notes
 * into the brain through {@link Promote}, which refuses if any quote fails.
 */
final class Review {

    private Review() {}

    static int run(Path notesDir, Path home, List<String> args) throws IOException {
        String topic = args.stream().filter(a -> !a.startsWith("--")).findFirst().orElse(null);
        boolean yes = args.contains("--yes");
        Path drafts = notesDir.resolve("_draft");
        if (topic == null) {
            return list(drafts);
        }
        if (!Promote.PROJECT.matcher(topic).matches()) {
            System.err.println("topic must be lowercase letters, digits and dashes: " + topic);
            return 2;
        }
        Path folder = drafts.resolve(topic);
        if (!Files.isDirectory(folder)) {
            System.err.println("Nothing to review for \"" + topic + "\". Waiting: " + (Files.isDirectory(drafts) ? waiting(drafts) : "nothing"));
            return 2;
        }
        List<Note> notes = Note.load(folder, true);
        Map<String, String> problems = Check.run(notes, home).stream()
                .collect(Collectors.toMap(p -> p.note().id(), Check.Problem::message, (a, b) -> a));
        System.out.printf("%d new note%s for \"%s\". Read them:%n%n", notes.size(), notes.size() == 1 ? "" : "s", topic);
        int n = 1;
        for (Note note : notes) {
            String problem = problems.get(note.id());
            System.out.printf("%2d. %s   %s%n      \"%s\"%n      from %s%n%n", n++, note.title(),
                    problem == null ? "[quote verified]" : "[NOT VERIFIED: " + problem + "]", note.quote(), note.source());
        }
        if (!problems.isEmpty()) {
            System.out.println("Fix or delete the unverified drafts (they are files in notes/_draft/" + topic + "/), then review again.");
            return 1;
        }
        if (!yes) {
            if (System.console() == null) {
                System.out.println("Nothing added yet. To add them: brain review " + topic + " --yes");
                return 0;
            }
            String answer = System.console().readLine("Add these %d notes to your brain? [y/N] ", notes.size());
            if (answer == null || !answer.strip().toLowerCase().startsWith("y")) {
                System.out.println("Not added. The drafts are still there.");
                return 0;
            }
        }
        return Promote.run(notesDir, home, topic);
    }

    private static int list(Path drafts) throws IOException {
        String waiting = Files.isDirectory(drafts) ? waiting(drafts) : "";
        if (waiting.isEmpty()) {
            System.out.println("Nothing to review. Add something first: brain add <video|slack-link>, then /learn in Claude Code.");
            return 0;
        }
        System.out.println("Waiting for your review: " + waiting);
        System.out.println("Review one: brain review <topic>");
        return 0;
    }

    private static String waiting(Path drafts) throws IOException {
        try (Stream<Path> topics = Files.list(drafts)) {
            return topics.filter(Files::isDirectory).sorted().map(t -> {
                try (Stream<Path> files = Files.list(t)) {
                    return t.getFileName() + " (" + files.filter(f -> f.toString().endsWith(".md")).count() + " notes)";
                } catch (IOException e) {
                    return t.getFileName().toString();
                }
            }).collect(Collectors.joining(", "));
        }
    }
}
