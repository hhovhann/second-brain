package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The human gate, made mechanical: drafts become trusted notes only if every quote still
 * checks out against its source and no id collides with a note already trusted.
 *
 * <p>The person reviews the draft files; this command refuses to move any that fail.
 */
final class Promote {

    static final Pattern PROJECT = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private Promote() {}

    static int run(Path brainDir, Path root, String project) throws IOException {
        if (!PROJECT.matcher(project).matches()) {
            System.err.println("topic must be lowercase letters, digits and dashes: " + project);
            return 2;
        }
        Path draftDir = brainDir.resolve("_draft").resolve(project);
        if (!Files.isDirectory(draftDir)) {
            System.err.println("no drafts at " + draftDir);
            return 2;
        }
        List<Note> drafts = Note.load(draftDir, true);
        List<Note> trusted = Note.loadAll(brainDir);
        List<Check.Problem> problems = new ArrayList<>(Check.run(drafts, root));
        for (Note draft : drafts) {
            if (trusted.stream().anyMatch(t -> t.id().equals(draft.id()) && !t.project().equals(project))) {
                problems.add(new Check.Problem(draft, "id already used by a trusted note in another project"));
            }
        }
        // A draft may only point at a note that will exist: itself a draft, or already trusted.
        List<String> known = new ArrayList<>(drafts.stream().map(Note::id).toList());
        known.addAll(trusted.stream().map(Note::id).toList());
        for (Note draft : drafts) {
            if (draft.supersededBy() != null && !known.contains(draft.supersededBy())) {
                problems.add(new Check.Problem(draft, "superseded_by points at unknown note"));
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.out.printf("FAIL  %-28s %s%n", p.note().id(), p.message()));
            System.out.println("Nothing was promoted. Fix the drafts and run again.");
            return 1;
        }
        Path target = brainDir.resolve(project);
        Files.createDirectories(target);
        for (Note draft : drafts) {
            Files.move(draft.file(), target.resolve(draft.file().getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        try (Stream<Path> left = Files.list(draftDir)) {
            if (left.findAny().isEmpty()) {
                Files.delete(draftDir);
            }
        }
        System.out.printf("Added %d note%s to notes/%s/ — every quote verified.%n", drafts.size(), drafts.size() == 1 ? "" : "s", project);
        return 0;
    }
}
