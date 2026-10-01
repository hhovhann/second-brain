package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitTest {

    @TempDir
    Path cwd;

    @Test
    void aNewBrainHasTwoFoldersAndTheClaudeCommands() throws IOException {
        assertThat(Init.run(cwd, List.of("my-brain"))).isZero();
        Path home = cwd.resolve("my-brain");
        assertThat(home.resolve("notes")).isDirectory();
        assertThat(home.resolve("sources")).isDirectory();
        assertThat(home.resolve(".claude/commands/learn.md")).isRegularFile();
        assertThat(home.resolve(".claude/commands/ask.md")).isRegularFile();
    }

    @Test
    void runningItAgainNeverOverwritesYourFiles() throws IOException {
        Init.run(cwd, List.of("b"));
        Path readme = cwd.resolve("b/README.md");
        Files.writeString(readme, "my own words");
        Init.run(cwd, List.of("b"));
        assertThat(readme).hasContent("my own words");
    }
}
