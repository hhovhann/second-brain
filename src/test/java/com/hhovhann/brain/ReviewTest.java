package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewTest {

    @TempDir
    Path home;

    private Path notes;

    @BeforeEach
    void source() throws IOException {
        notes = Files.createDirectories(home.resolve("notes"));
        Files.createDirectories(home.resolve("sources"));
        Files.writeString(home.resolve("sources/talk.md"), "[00:00:03] We dropped the graph database because it was unused.");
    }

    private Path draft(String id, String quote) throws IOException {
        Path file = Files.createDirectories(notes.resolve("_draft/talk-topic")).resolve(id + ".md");
        Files.writeString(file, """
                ---
                id: %s
                type: decision
                valid_from: 2026-09-01
                repo: sources
                source: file:talk.md#t=00:00:03
                quote: %s
                ---
                # Title %s
                Body.
                """.formatted(id, quote, id));
        return file;
    }

    @Test
    void withoutAConfirmationNothingIsAdded() throws IOException {
        Path d = draft("a", "dropped the graph database because it was unused");
        assertThat(Review.run(notes, home, List.of("talk-topic"))).isZero();   // no console in tests, no --yes
        assertThat(d).exists();
        assertThat(notes.resolve("talk-topic")).doesNotExist();
    }

    @Test
    void yesAddsVerifiedNotesToTheBrain() throws IOException {
        draft("a", "dropped the graph database because it was unused");
        assertThat(Review.run(notes, home, List.of("talk-topic", "--yes"))).isZero();
        assertThat(notes.resolve("talk-topic/a.md")).exists();
        assertThat(notes.resolve("_draft/talk-topic")).doesNotExist();
    }

    @Test
    void aMisquotedDraftBlocksEverythingEvenWithYes() throws IOException {
        Path good = draft("good", "dropped the graph database because it was unused");
        draft("bad", "the owner rejected the change entirely");
        assertThat(Review.run(notes, home, List.of("talk-topic", "--yes"))).isEqualTo(1);
        assertThat(good).exists();
        assertThat(notes.resolve("talk-topic")).doesNotExist();
    }

    @Test
    void aTopicThatIsNotAFolderNameIsRefused() throws IOException {
        assertThat(Review.run(notes, home, List.of("../etc", "--yes"))).isEqualTo(2);
    }
}
