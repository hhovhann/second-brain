package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckTest {

    @TempDir
    Path root;

    private Note note(String id, String source, String quote, String extra) throws IOException {
        Path file = root.resolve("brain").resolve(id + ".md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                ---
                id: %s
                type: decision
                valid_from: 2026-09-01
                repo: repo
                source: %s
                quote: %s
                %s
                ---
                # Title %s
                Body.
                """.formatted(id, source, quote, extra, id));
        return Note.parse(file);
    }

    private void source(String name, String text) throws IOException {
        Path file = root.resolve("repo").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void aQuoteThatOccursInItsSourcePasses_evenWhenWrappedAndRecased() throws IOException {
        source("doc.md", "The Spring AI track never went past\nplain chat, and it was removed.");
        Note n = note("a", "file:doc.md", "never went past plain CHAT", "");
        assertThat(Check.run(List.of(n), root)).isEmpty();
    }

    @Test
    void aParaphraseFails() throws IOException {
        source("doc.md", "The Spring AI track never went past plain chat.");
        Note n = note("a", "file:doc.md", "it only ever supported simple chat", "");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("quote not found"));
    }

    @Test
    void aTooShortQuoteProvesNothing() throws IOException {
        source("doc.md", "PageGraph was removed.");
        Note n = note("a", "file:doc.md", "PageGraph", "");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("too short"));
    }

    @Test
    void aTimestampFragmentPointsAtAMomentButOnlyThePathIsRead() throws IOException {
        source("transcript.md", "[00:12:03] We dropped the graph database because it was unused.");
        Note n = note("a", "file:transcript.md#t=00:12:03", "dropped the graph database because it was unused", "");
        assertThat(Check.run(List.of(n), root)).isEmpty();
    }

    @Test
    void aFragmentCannotSmuggleInAPathThatEscapes() throws IOException {
        Files.writeString(root.resolve("secret.txt"), "top secret value here");
        Note n = note("a", "file:../secret.txt#t=1", "top secret value here", "");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("escapes the repository"));
    }

    @Test
    void aPathThatEscapesTheRepositoryIsRejected() throws IOException {
        Files.writeString(root.resolve("secret.txt"), "top secret value here");
        Note n = note("a", "file:../secret.txt", "top secret value here", "");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("escapes the repository"));
    }

    @Test
    void aSourceThatIsNotAHashIsNeverPassedToGit() throws IOException {
        Note n = note("a", "git:--output=/tmp/x", "some long enough quote", "");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("not a commit hash"));
    }

    @Test
    void supersessionMustPointAtARealNote() throws IOException {
        source("doc.md", "A sufficiently long quote lives here.");
        Note n = note("a", "file:doc.md", "sufficiently long quote", "status: superseded\nsuperseded_by: ghost");
        assertThat(Check.run(List.of(n), root)).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("unknown note 'ghost'"));
    }
}
