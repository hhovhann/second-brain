package com.hhovhann.brain;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one command for getting knowledge in: {@code brain capture <anything> [topic]}.
 *
 * <p>It reads the input (repo, folder, file, PDF, Word, PowerPoint, image, web page, Google Doc,
 * video, recording, Slack thread), has the local model propose notes, lets code verify every
 * quote against the source, writes the survivors as drafts, and then hands over to the review
 * step where the person accepts them. Nothing here needs a hosted model.
 */
final class Capture {

    private Capture() {}

    static int run(Path notesDir, Path home, List<String> args) throws IOException, InterruptedException {
        String input = null;
        String topic = null;
        boolean yes = false;
        boolean screen = false;
        boolean history = false;
        for (String arg : args) {
            switch (arg) {
                case "--history" -> history = true;
                case "--yes" -> yes = true;
                case "--screen" -> screen = true;
                default -> {
                    if (input == null) {
                        input = arg;
                    } else if (topic == null) {
                        topic = arg;
                    }
                }
            }
        }
        if (input == null) {
            System.err.println("usage: brain capture <anything> [topic] [--yes] [--history]\n"
                    + "  anything = a repo or folder, a file (pdf, docx, pptx, md, txt, image, subtitles), a web page or\n"
                    + "             Google Doc link, a video link or recording, or a Slack message link");
            return 2;
        }
        if (topic != null && !Promote.PROJECT.matcher(topic).matches()) {
            System.err.println("topic must be lowercase letters, digits and dashes: " + topic);
            return 2;
        }
        System.out.println("Reading " + input + " ...");
        Reader.Gathered gathered;
        try {
            gathered = Reader.gather(home, input, screen);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not read it: " + e.getMessage());
            return 1;
        }
        String name = topic != null ? topic : gathered.topic();
        System.out.printf("Read %s: %,d characters in %d part%s. Finding what is worth keeping (local model)...%n",
                gathered.label(), gathered.units().stream().mapToInt(u -> u.text().length()).sum(),
                gathered.units().size(), gathered.units().size() == 1 ? "" : "s");

        Set<String> taken = new HashSet<>();
        if (Files.isDirectory(notesDir)) {
            Note.load(notesDir, true).forEach(n -> taken.add(n.id()));
        }
        Extract.Result result;
        try {
            ChatModel chat = Models.extractor();
            result = Extract.run(gathered.units(), (system, user) ->
                    chat.chat(SystemMessage.from(system), UserMessage.from(user)).aiMessage().text(), taken, System.out::println, history);
        } catch (RuntimeException e) {
            System.err.println("The local model did not answer (" + rootMessage(e) + "). Start LM Studio and load the models; `brain doctor` checks.");
            return 1;
        }
        if (result.notes().isEmpty()) {
            System.out.printf("Nothing worth keeping was found (%d proposed, %d had a quote that is not in the source).%n"
                    + "A bigger or more detailed source usually helps.%n", result.proposed(), result.dropped());
            return 0;
        }
        Path drafts = Files.createDirectories(notesDir.resolve("_draft").resolve(name));
        for (Extract.Candidate c : result.notes()) {
            Files.writeString(drafts.resolve(c.id() + ".md"), render(c));
        }
        // The same gate again, on the files as written: anything that does not verify is deleted, not offered.
        List<Note> written = Note.load(drafts, true);
        for (Check.Problem problem : Check.run(written, home)) {
            Files.deleteIfExists(problem.note().file());
        }
        System.out.printf("%n%d note%s drafted; %d proposal%s dropped because the quote was not found in the source%s.%n%n",
                result.notes().size(), result.notes().size() == 1 ? "" : "s", result.dropped(), result.dropped() == 1 ? "" : "s",
                result.replaced() > 0 ? "; " + result.replaced() + " marked as replaced by a later fact (the model's guess: check them below)" : "");
        return Review.run(notesDir, home, yes ? List.of(name, "--yes") : List.of(name));
    }

    static String render(Extract.Candidate c) {
        StringBuilder out = new StringBuilder("---\n")
                .append("id: ").append(c.id()).append('\n')
                .append("type: ").append(c.type()).append('\n')
                .append("valid_from: ").append(c.date()).append('\n');
        if (c.validTo() != null) {
            out.append("valid_to: ").append(c.validTo()).append('\n');
        }
        out.append("status: ").append(c.status()).append('\n');
        if (c.supersededBy() != null) {
            out.append("superseded_by: ").append(c.supersededBy()).append('\n');
        }
        return out.append("repo: ").append(c.repo()).append('\n')
                .append("source: ").append(c.source()).append('\n')
                .append("quote: ").append(c.quote()).append('\n')
                .append("---\n# ").append(c.title()).append('\n')
                .append(c.body().isEmpty() ? c.title() : c.body()).append('\n').toString();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
