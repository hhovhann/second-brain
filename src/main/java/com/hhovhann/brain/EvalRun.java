package com.hhovhann.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the gold questions and writes a report. The automatic part is deliberately crude
 * (substring matches, and the decline check); "right" is still a human judgement, and the
 * report says so instead of printing one flattering number.
 */
final class EvalRun {

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    record Question(String id, String kind, String ask, LocalDate asOf, List<String> expect, List<String> forbid) {}

    private EvalRun() {}

    static List<Question> parse(Path file) throws IOException {
        List<Question> questions = new ArrayList<>();
        String id = null, kind = null, ask = null;
        LocalDate asOf = null;
        List<String> expect = List.of(), forbid = List.of();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.strip();
            if (line.startsWith("- id:")) {
                if (id != null) {
                    questions.add(new Question(id, kind, ask, asOf, expect, forbid));
                }
                id = line.substring(5).strip();
                kind = null; ask = null; asOf = null; expect = List.of(); forbid = List.of();
            } else if (line.startsWith("kind:")) {
                kind = line.substring(5).strip();
            } else if (line.startsWith("ask:")) {
                ask = line.substring(4).strip();
            } else if (line.startsWith("as_of:")) {
                asOf = LocalDate.parse(line.substring(6).strip());
            } else if (line.startsWith("expect:")) {
                expect = quoted(line);
            } else if (line.startsWith("forbid:")) {
                forbid = quoted(line);
            }
        }
        if (id != null) {
            questions.add(new Question(id, kind, ask, asOf, expect, forbid));
        }
        return questions;
    }

    private static List<String> quoted(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = QUOTED.matcher(line);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    static int run(Path brainDir, Path root, Path questionsFile, String project) throws IOException {
        List<Question> questions = parse(questionsFile);
        StringBuilder report = new StringBuilder("# Eval report — ")
                .append(questionsFile.getFileName())
                .append("\n\nAutomatic checks are crude substring matches. **Right/wrong still needs a human read.**\n\n");
        int declinedOk = 0, forbidden = 0;
        for (Question q : questions) {
            Ask.Answer a = Brain.answer(brainDir, q.ask(), project, q.asOf());
            String text = a == null ? "" : a.text();
            String lower = text.toLowerCase(Locale.ROOT);
            long hit = q.expect().stream().filter(e -> lower.contains(e.toLowerCase(Locale.ROOT))).count();
            List<String> leaked = q.forbid().stream().filter(f -> lower.contains(f.toLowerCase(Locale.ROOT))).toList();
            boolean decline = "decline".equals(q.kind());
            boolean declined = a != null && a.declined();
            if (decline && declined) declinedOk++;
            if (!leaked.isEmpty() || (decline && !declined)) forbidden++;
            report.append("## ").append(q.id()).append(" · ").append(q.kind()).append(" — ").append(q.ask()).append("\n\n")
                    .append(text.isBlank() ? "(no answer)" : text).append("\n\n")
                    .append("- cited notes: ").append(a == null ? "-" : a.cited().stream().map(h -> h.note().id()).toList()).append("\n")
                    .append("- expected phrases found verbatim: ").append(hit).append("/").append(q.expect().size()).append(" (crude)\n");
            if (!leaked.isEmpty()) report.append("- ⚠ FORBIDDEN phrases present: ").append(leaked).append("\n");
            if (decline) report.append("- should decline: ").append(declined ? "yes, it did" : "**NO — it answered**").append("\n");
            report.append("\n");
            System.out.printf("%-4s %-9s cited=%d expectHits=%d/%d%s%n", q.id(), q.kind(),
                    a == null ? 0 : a.cited().size(), hit, q.expect().size(),
                    leaked.isEmpty() ? "" : "  FORBIDDEN " + leaked);
        }
        Path out = root.resolve("eval-report-" + (project == null ? "all" : project) + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.printf("%d questions, %d forbidden/leaked. Report: %s%n", questions.size(), forbidden, out);
        return forbidden == 0 ? 0 : 1;
    }
}
