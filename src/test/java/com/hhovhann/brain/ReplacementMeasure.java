package com.hhovhann.brain;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Measurement, not a unit test: runs the replacement pass with the live model over an existing set of notes. */
@Tag("measure")
class ReplacementMeasure {

    @Test
    void replacementsOverExistingNotes() throws Exception {
        String dir = System.getenv("BRAIN_MEASURE_NOTES");
        if (dir == null) {
            return;
        }
        List<Extract.Candidate> notes = Note.load(Path.of(dir), false).stream()
                .map(n -> new Extract.Candidate(n.id(), n.type(), n.validFrom(), n.title(), "", n.quote(), n.repo(), n.source(),
                        "current", null, null))
                .toList();
        var chat = Models.extractor();
        Extract.Replaced r = Extract.markReplacements(notes,
                (s, u) -> chat.chat(SystemMessage.from(s), UserMessage.from(u)).aiMessage().text(), System.out::println);
        StringBuilder out = new StringBuilder("\nREPLACED %d of %d notes:\n".formatted(r.count(), notes.size()));
        for (Extract.Candidate c : r.notes()) {
            if (c.supersededBy() != null) {
                String by = r.notes().stream().filter(x -> x.id().equals(c.supersededBy())).findFirst().get().title();
                out.append("  [%s] %s\n     -> [%s] %s\n".formatted(c.date(), c.title(), c.validTo(), by));
            }
        }
        Files.writeString(Path.of(System.getenv().getOrDefault("BRAIN_MEASURE_OUT", "/tmp/replacements.txt")), out.toString());
    }
}
