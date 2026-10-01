package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordTest {

    private static Note note(String id, String title, String body, String quote) {
        return new Note(id, "decision", LocalDate.parse("2026-09-01"), null, "current", null,
                "repo", "file:x", quote, title, "# " + title + "\n" + body, Path.of("notes/p/" + id + ".md"));
    }

    @Test
    void anExactNameFindsTheNoteThatContainsIt() {
        List<Note> notes = List.of(
                note("a", "Storage choice", "Plain files are used for storage.", "files are used"),
                note("b", "Old stack", "Neo4j and Redis were replaced.", "Neo4j and Redis"),
                note("c", "Naming", "The tool is called second-brain.", "called second-brain"));
        assertThat(Keyword.rank(notes, "Why did you drop Neo4j?")).containsExactly(1);
    }

    @Test
    void versionNumbersAreKeptWhole() {
        assertThat(Keyword.tokens("Moved to Gradle 9.7.1.")).contains("gradle", "9.7.1");
    }

    @Test
    void aQuestionWithNoSharedWordRanksNothing() {
        List<Note> notes = List.of(note("a", "Storage", "Plain files.", "plain files"));
        assertThat(Keyword.rank(notes, "Which cloud provider is used?")).isEmpty();
    }

    @Test
    void fusionLiftsANoteThatEitherSearchLikesAndMostOneBothLike() {
        // meaning ranks 0,1,2,3; words rank 3 first. Note 3 is liked by both, so it beats 0..2 only if both agree.
        List<Integer> fused = Keyword.fuse(List.of(0, 1, 2, 3), List.of(3, 0));
        assertThat(fused).startsWith(0, 3);
        assertThat(fused).containsExactlyInAnyOrder(0, 1, 2, 3);
    }
}
