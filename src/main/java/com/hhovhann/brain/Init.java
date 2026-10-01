package com.hhovhann.brain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Makes a brain folder: two folders, the Claude Code commands, and a config that lets Claude Code
 * search the brain by itself. Never overwrites a file that already exists.
 */
final class Init {

    /** Built-in starter files: where they are inside the program, and where they go in a new brain. */
    private static final String[][] STARTER = {
        {"README.md", "README.md"},
        {"CLAUDE.md", "CLAUDE.md"},
        {"capture.md", ".claude/commands/capture.md"},
        {"ask.md", ".claude/commands/ask.md"},
    };

    private Init() {}

    static int run(Path cwd, List<String> args) throws IOException {
        return run(cwd, args, Path.of(Models.env("BRAIN_TOOL_DIR", ".")).toAbsolutePath().normalize());
    }

    static int run(Path cwd, List<String> args, Path tool) throws IOException {
        Path home = args.isEmpty() ? cwd : cwd.resolve(args.get(0)).normalize();
        Files.createDirectories(home.resolve("notes"));
        Files.createDirectories(home.resolve("sources"));
        int copied = 0;
        for (String[] file : STARTER) {
            Path to = home.resolve(file[1]);
            if (!Files.exists(to)) {
                try (InputStream in = Init.class.getResourceAsStream("/starter/" + file[0])) {
                    if (in == null) {
                        throw new IOException("missing built-in file " + file[0]);
                    }
                    Files.createDirectories(to.getParent());
                    Files.copy(in, to);
                    copied++;
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
        Path launcher = tool.resolve("brain");
        boolean mcp = Files.isRegularFile(launcher);
        if (mcp) {
            writeMcpConfig(home, launcher);
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
                %s%s""", where,
                mcp ? """

                        Let Claude Code use the brain by itself (it searches it before answering "why" questions):
                          in this folder:      already set up (.mcp.json); approve it the first time Claude Code asks
                          from any project:    claude mcp add --scope user brain -e BRAIN_HOME=%s -- %s mcp
                        """.formatted(home, launcher) : "",
                "");
        return 0;
    }

    /** A project-level MCP config, so Claude Code opened in this folder can search the brain. */
    private static void writeMcpConfig(Path home, Path launcher) throws IOException {
        Path file = home.resolve(".mcp.json");
        if (Files.exists(file)) {
            return;
        }
        ObjectMapper json = new ObjectMapper();
        ObjectNode server = json.createObjectNode().put("command", launcher.toString());
        server.putArray("args").add("mcp");
        server.putObject("env").put("BRAIN_HOME", home.toString());
        ObjectNode root = json.createObjectNode();
        root.putObject("mcpServers").set("brain", server);
        Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
    }
}
