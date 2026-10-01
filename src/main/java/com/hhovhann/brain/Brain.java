package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Entry point: {@code doctor}, {@code check}, {@code ask}. See README.md. */
public final class Brain {

    private Brain() {}

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "help" : args[0];
        List<String> rest = List.of(args).subList(Math.min(1, args.length), args.length);
        // Your brain is the folder you are in (or BRAIN_HOME): notes/ holds the notes, sources/ the text copies.
        Path home = Path.of(Models.env("BRAIN_HOME", "")).toAbsolutePath();
        Path dir = home.resolve("notes");
        if (List.of("check", "verify", "review", "promote", "eval", "ask", "mcp").contains(command) && !java.nio.file.Files.isDirectory(dir)) {
            System.err.println("""
                    There is no brain in this folder (no notes/ here).
                      Make one:         brain init my-brain      then: cd my-brain
                      Try the demo:     cd examples/self-demo    (inside the second-brain folder)
                      Use another one:  BRAIN_HOME=/path/to/my-brain brain ask "..." """);
            System.exit(2);
        }
        switch (command) {
            case "doctor" -> System.exit(Doctor.run(System.out));
            case "init" -> System.exit(Init.run(Path.of("").toAbsolutePath(), rest));
            case "add", "ingest" -> System.exit(Ingest.run(home, rest));
            case "review", "promote" -> System.exit(Review.run(dir, home, rest));
            case "verify", "check" -> System.exit(check(dir, home, rest.contains("--drafts")));
            case "eval" -> System.exit(rest.isEmpty() ? 2 : EvalRun.run(dir, home, Path.of(rest.get(0)), rest.size() > 1 ? rest.get(1) : null));
            case "ask" -> System.exit(askCommand(dir, rest));
            case "mcp" -> System.exit(Mcp.serve(dir, System.in, System.out));
            default -> {
                System.out.println("""
                        second-brain: ask why, get the answer with a verified quote

                        The whole thing is three steps:
                          1. add      brain add <video-url | recording | slack-link>   (repos and text files need no add)
                                      then, in Claude Code:  /learn <what> <topic>
                          2. review   brain review <topic>        read the new notes, then accept them
                          3. ask      brain ask "why did we ...?"

                        Other commands:
                          init [folder]   make a new brain folder (notes/ and sources/)
                          verify          check every note's quote really occurs in its source (no AI)
                          mcp             serve this brain read-only to Claude Code and other MCP clients
                          doctor          check Java 27 and the local model server
                          eval <file>     run a file of test questions

                        ask options: --topic <name> (one topic only), --as-of YYYY-MM-DD (as it was then)
                        Run inside your brain folder, or set BRAIN_HOME. More: README.md""");
                System.exit(command.equals("help") ? 0 : 2);
            }
        }
    }

    private static int check(Path dir, Path root, boolean drafts) throws IOException {
        List<Note> notes = Note.load(dir, drafts);
        if (drafts) {
            notes = notes.stream().filter(n -> n.file().toString().contains("/_draft/")).toList();
        }
        List<Check.Problem> problems = Check.run(notes, root);
        for (Check.Problem p : problems) {
            System.out.printf("FAIL  %-28s %s%n", p.note().id(), p.message());
        }
        long current = notes.stream().filter(Note::isCurrent).count();
        System.out.printf(
                "%d notes (%d current, %d superseded): %s%n",
                notes.size(),
                current,
                notes.size() - current,
                problems.isEmpty() ? "every quote verified against its source" : problems.size() + " problem(s)");
        return problems.isEmpty() ? 0 : 1;
    }

    static int askCommand(Path dir, List<String> words) throws IOException {
        String project = null;
        java.time.LocalDate asOf = null;
        List<String> question = new java.util.ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            switch (words.get(i)) {
                case "--topic", "--project" -> project = words.get(++i);
                case "--as-of" -> asOf = java.time.LocalDate.parse(words.get(++i));
                default -> question.add(words.get(i));
            }
        }
        return ask(dir, String.join(" ", question), project, asOf, true);
    }

    static int ask(Path dir, String question, String project, java.time.LocalDate asOf, boolean print) throws IOException {
        Ask.Answer answer = answer(dir, question, project, asOf);
        if (answer == null) {
            System.err.println("usage: ask <question of 1-1000 characters>");
            return 2;
        }
        System.out.println(render(answer, asOf));
        return 0;
    }

    static Ask.Answer answer(Path dir, String question, String project, java.time.LocalDate asOf) throws IOException {
        if (question.isBlank() || question.length() > 1000) {
            return null;
        }
        final java.time.LocalDate day = asOf;
        List<Note> notes = select(dir, project, day);
        if (notes.isEmpty()) {
            return new Ask.Answer("NOT_IN_THE_NOTES", List.of(), false, true);
        }
        List<Ask.Hit> hits = Ask.retrieve(notes, Models.embeddings(), question, 8);
        return Ask.answer(Models.chat(), hits, question, asOf);
    }

    /** The trusted notes a question may use: one topic if given, and only those true on {@code day} if given. */
    static List<Note> select(Path dir, String project, java.time.LocalDate day) throws IOException {
        return Note.loadAll(dir).stream()
                .filter(n -> project == null || n.project().equals(project))
                .filter(n -> day == null || n.validOn(day))
                .toList();
    }

    static String render(Ask.Answer answer, java.time.LocalDate asOf) {
        StringBuilder out = new StringBuilder(answer.text()).append('\n');
        if (answer.declined()) {
            out.append("\n(The notes do not answer this. That is a gap, not a guess.)\n");
        } else if (answer.uncited()) {
            out.append("\n⚠ the answer cites no note — do not trust it.\n");
        }
        if (!answer.cited().isEmpty()) {
            out.append("\nSources (printed from the notes, not by the model):\n");
            for (Ask.Hit hit : answer.cited()) {
                Note n = hit.note();
                out.append("  [%d] %s/%s — %s, %s%n      \"%s\"%n      %s%n".formatted(
                        hit.number(), n.project(), n.title(),
                        asOf != null ? "true on " + asOf : n.isCurrent() ? "current" : "NO LONGER TRUE",
                        n.validFrom() + " → " + (n.validTo() == null ? "now" : n.validTo()),
                        n.quote(), n.source()));
            }
        }
        return out.toString();
    }
}
