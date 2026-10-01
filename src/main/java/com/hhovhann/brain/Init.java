package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Makes a brain folder: two folders and the Claude Code commands, nothing else.
 * Never overwrites a file that already exists.
 */
final class Init {

    private Init() {}

    static int run(Path cwd, List<String> args) throws IOException {
        Path home = args.isEmpty() ? cwd : cwd.resolve(args.get(0)).normalize();
        Files.createDirectories(home.resolve("notes"));
        Files.createDirectories(home.resolve("sources"));
        Path starter = Path.of(Models.env("BRAIN_TOOL_DIR", ".")).resolve("starter");
        int copied = 0;
        if (Files.isDirectory(starter)) {
            try (Stream<Path> files = Files.walk(starter)) {
                for (Path from : files.filter(Files::isRegularFile).toList()) {
                    Path to = home.resolve(starter.relativize(from).toString());
                    if (!Files.exists(to)) {
                        Files.createDirectories(to.getParent());
                        Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
                        copied++;
                    }
                }
            }
        }
        if (!Files.exists(home.resolve(".git"))) {
            try {
                new ProcessBuilder("git", "init", "-q", home.toString()).inheritIO().start().waitFor();
            } catch (IOException | InterruptedException e) {
                // no git is fine: the brain is just folders
            }
        }
        String where = args.isEmpty() ? "this folder" : args.get(0);
        System.out.printf("""
                Your brain is ready in %s:

                  notes/     the brain itself: small verified notes, one fact per file
                  sources/   text copies of the videos, recordings and chats you add

                Next (from inside that folder):
                  1. brain add <video-url | recording | slack-link>      (skip for repos and text files)
                  2. open Claude Code here and run:  /learn <what> <topic>
                  3. brain review <topic>        then:   brain ask "why did we ...?"
                %s""", where, copied == 0 && !Files.isDirectory(starter)
                ? "\n(The Claude Code commands were not copied: BRAIN_TOOL_DIR does not point at second-brain.)\n" : "");
        return 0;
    }
}
