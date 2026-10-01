package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Entry point: {@code doctor}, {@code check}, {@code ask}. See README.md. */
public final class Brain {

    private Brain() {}

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "help" : args[0];
        Path root = Path.of("").toAbsolutePath();
        Path dir = root.resolve(Models.env("BRAIN_DIR", "brain"));
        if (List.of("check", "promote", "eval", "ask").contains(command) && !java.nio.file.Files.isDirectory(dir)) {
            System.err.println("No notes at " + dir + ". Try the bundled example: BRAIN_DIR=examples/brain "
                    + "(see README.md), or run /capture to create your own.");
            System.exit(2);
        }
        switch (command) {
            case "doctor" -> System.exit(Doctor.run(System.out));
            case "check" -> System.exit(check(dir, root, List.of(args).contains("--drafts")));
            case "promote" -> System.exit(args.length < 2 ? 2 : Promote.run(dir, root, args[1]));
            case "eval" -> System.exit(args.length < 2 ? 2 : EvalRun.run(dir, root, Path.of(args[1]), args.length > 2 ? args[2] : null));
            case "ask" -> System.exit(askCommand(dir, List.of(args).subList(1, args.length)));
            default -> {
                System.out.println("""
                        second-brain

                          check [--drafts]  verify every note's quote really occurs in its source
                          promote <project> move reviewed drafts into brain/ (refuses if any quote fails)
                          eval <file> [project]  run the gold questions and write a report
                          ask [--project p] [--as-of YYYY-MM-DD] <question>
                                         answer from the notes, with the quotes behind the answer
                          doctor         check Java 27, scoped values, and the local model server

                        Notes live in brain/ (set BRAIN_DIR to use another folder). See README.md.""");
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
                case "--project" -> project = words.get(++i);
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
        List<Note> notes = Note.loadAll(dir).stream()
                .filter(n -> project == null || n.project().equals(project))
                .filter(n -> day == null || n.validOn(day))
                .toList();
        if (notes.isEmpty()) {
            return new Ask.Answer("NOT_IN_THE_NOTES", List.of(), false, true);
        }
        List<Ask.Hit> hits = Ask.retrieve(notes, Models.embeddings(), question, 6);
        return Ask.answer(Models.chat(), hits, question, asOf);
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
