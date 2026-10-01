package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtractTest {

    private static final Reader.Unit COMMIT = new Reader.Unit(
            "Remove the Spring AI track\n\nIt never went past plain chat and two frameworks would double every step.",
            "../proj", "git:ae2f723", LocalDate.parse("2026-09-26"));
    private static final Reader.Unit TALK = new Reader.Unit(
            "[00:00:03] We moved billing to Postgres.\n[00:00:09] The owner approved the budget of 40 thousand.",
            "sources", "file:video-1.md", LocalDate.parse("2026-10-01"));
    private static final Reader.Unit SLIDES = new Reader.Unit(
            "[slide 1] Launch plan\n[slide 2] The public beta opens on 3 November.",
            "sources", "file:doc-deck.md", LocalDate.parse("2026-10-02"));

    private static Extract.Llm replying(String... replies) {
        List<String> queue = new ArrayList<>(List.of(replies));
        return (system, user) -> queue.isEmpty() ? "[]" : queue.remove(0);
    }

    private static Extract.Result run(List<Reader.Unit> units, Extract.Llm llm) {
        return Extract.run(units, llm, Set.of(), m -> { });
    }

    @Test
    void aFactWhoseQuoteIsInTheSourceBecomesANoteWithTheRealCommitAsItsSource() {
        Extract.Result r = run(List.of(COMMIT), replying(
                "[{\"type\":\"decision\",\"title\":\"Spring AI track removed\",\"body\":\"Two frameworks doubled the work.\","
                        + "\"quote\":\"two frameworks would double every step\",\"date\":null}]"));
        assertThat(r.notes()).singleElement().satisfies(n -> {
            assertThat(n.source()).isEqualTo("git:ae2f723");
            assertThat(n.repo()).isEqualTo("../proj");
            assertThat(n.date()).isEqualTo(LocalDate.parse("2026-09-26"));
            assertThat(n.id()).isEqualTo("spring-ai-track-removed");
        });
    }

    @Test
    void aQuoteThatIsNotInTheSourceIsDroppedHoweverConfident() {
        Extract.Result r = run(List.of(COMMIT), replying(
                "[{\"type\":\"decision\",\"title\":\"Budget approved\",\"body\":\"x\",\"quote\":\"the board approved a larger budget\",\"date\":\"2026-09-26\"}]"));
        assertThat(r.notes()).isEmpty();
        assertThat(r.dropped()).isEqualTo(1);
    }

    @Test
    void theMomentInTheRecordingIsFoundByCodeNotByTheModel() {
        Extract.Result r = run(List.of(TALK), replying(
                "[{\"type\":\"decision\",\"title\":\"Budget approved\",\"body\":\"b\",\"quote\":\"The owner approved the budget of 40 thousand.\",\"date\":null}]"));
        assertThat(r.notes().get(0).source()).isEqualTo("file:video-1.md#t=00:00:09");
    }

    @Test
    void aSlideNumberBecomesTheAnchor() {
        Extract.Result r = run(List.of(SLIDES), replying(
                "[{\"type\":\"release\",\"title\":\"Beta opens\",\"body\":\"b\",\"quote\":\"The public beta opens on 3 November.\",\"date\":null}]"));
        assertThat(r.notes().get(0).source()).isEqualTo("file:doc-deck.md#slide=2");
    }

    @Test
    void aQuoteThatCarriesAMarkerOrAnEllipsisIsRefused() {
        Extract.Result r = run(List.of(TALK), replying(
                "[{\"title\":\"a\",\"quote\":\"[00:00:03] We moved billing to Postgres.\"},"
                        + "{\"title\":\"b\",\"quote\":\"We moved billing ... budget of 40 thousand\"}]"));
        assertThat(r.notes()).isEmpty();
    }

    @Test
    void anInventedDateInTheFutureFallsBackToTheSourceDate() {
        Extract.Result r = run(List.of(COMMIT), replying(
                "[{\"title\":\"Spring AI track removed\",\"quote\":\"never went past plain chat\",\"date\":\"2099-01-01\"}]"));
        assertThat(r.notes().get(0).date()).isEqualTo(LocalDate.parse("2026-09-26"));
    }

    @Test
    void theSameFactTwiceIsKeptOnce_andIdsNeverCollideWithExistingNotes() {
        Extract.Result r = Extract.run(List.of(COMMIT), replying(
                "[{\"title\":\"Spring AI removed\",\"quote\":\"never went past plain chat\"},"
                        + "{\"title\":\"Spring AI removed again\",\"quote\":\"never went past plain chat\"}]"),
                Set.of("spring-ai-removed"), m -> { });
        assertThat(r.notes()).singleElement().satisfies(n -> assertThat(n.id()).isEqualTo("spring-ai-removed-2"));
    }

    private static Extract.Candidate fact(String id, String title, String date) {
        return new Extract.Candidate(id, "decision", LocalDate.parse(date), title, "b", "quote of " + id, "r", "file:x", "current", null, null);
    }

    @Test
    void relatedFactsInDateOrderAreOfferedAsPairsAndUnrelatedOnesAreNot() {
        List<Extract.Candidate> notes = List.of(
                fact("a", "Spring AI added as a second track", "2026-09-07"),
                fact("b", "Spring AI track removed", "2026-09-26"),
                fact("c", "Gradle upgraded to nine", "2026-09-10"));
        List<int[]> pairs = Extract.pairs(notes);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0)).containsExactly(0, 1);
    }

    @Test
    void aLaterFactReplacesAnEarlierOneOnlyWhenTheModelSaysYesAndTheDatesAgree() {
        List<Extract.Candidate> notes = List.of(
                fact("a", "Spring AI added as a second track", "2026-09-07"),
                fact("b", "Spring AI track removed", "2026-09-26"));
        Extract.Replaced yes = Extract.markReplacements(notes, (s, u) -> "[1]", m -> { });
        assertThat(yes.count()).isEqualTo(1);
        assertThat(yes.notes().get(0)).satisfies(n -> {
            assertThat(n.status()).isEqualTo("superseded");
            assertThat(n.supersededBy()).isEqualTo("b");
            assertThat(n.validTo()).isEqualTo(LocalDate.parse("2026-09-26"));
        });
        assertThat(yes.notes().get(1).status()).isEqualTo("current");
        assertThat(Extract.markReplacements(notes, (s, u) -> "[]", m -> { }).count()).isZero();
        assertThat(Extract.markReplacements(notes, (s, u) -> "[7]", m -> { }).count()).isZero();      // a number that is not a pair
        assertThat(Extract.markReplacements(notes, (s, u) -> "no idea", m -> { }).count()).isZero();   // an unreadable answer
    }

    @Test
    void manyNotesAreCheckedInSmallBatchesInsteadOfSkipped() {
        List<Extract.Candidate> notes = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            notes.add(fact("n" + i, "Billing service migration step " + i, LocalDate.parse("2026-01-01").plusDays(i).toString()));
        }
        List<Integer> questions = new ArrayList<>();
        Extract.markReplacements(notes, (s, u) -> { questions.add(u.split("\n").length); return "[]"; }, m -> { });
        assertThat(questions.size()).isGreaterThan(5);
        assertThat(questions).allSatisfy(n -> assertThat(n).isLessThan(20));
    }

    @Test
    void factsFromTheSameDayAreNeverMarkedAsReplacingEachOther() {
        String facts = "[{\"title\":\"Moved to Postgres\",\"quote\":\"We moved billing to Postgres.\",\"date\":\"2026-03-12\"},"
                + "{\"title\":\"Budget approved\",\"quote\":\"The owner approved the budget of 40 thousand.\",\"date\":\"2026-03-12\"}]";
        Extract.Result r = run(List.of(TALK), replying(facts, "[{\"old\":\"moved-to-postgres\",\"new\":\"budget-approved\"}]"));
        assertThat(r.replaced()).isZero();
        assertThat(r.notes()).allSatisfy(n -> assertThat(n.status()).isEqualTo("current"));
    }

    @Test
    void replyJunkIsSurvivedAndANonAnsweringModelIsReported() {
        assertThat(Extract.parse("<think>hmm</think>\n```json\n[{\"title\":\"a\"}]\n```")).hasSize(1);
        Extract.Result r = run(List.of(COMMIT, TALK), replying("[]", "not json at all"));
        assertThat(r.notes()).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> run(List.of(COMMIT), (s, u) -> { throw new IllegalStateException("down"); }))
                .hasMessage("down");
    }

    @Test
    void aReplyThatCannotBeReadIsRetriedOnceWithAStricterInstruction() {
        List<String> asked = new ArrayList<>();
        Extract.Llm llm = (system, user) -> {
            asked.add(user);
            return asked.size() == 1 ? "Sure! Here are the facts you asked for." : "[{\"title\":\"Spring AI removed\",\"quote\":\"never went past plain chat\"}]";
        };
        Extract.Result r = run(List.of(COMMIT), llm);
        assertThat(r.notes()).hasSize(1);
        assertThat(r.failedParts()).isZero();
        assertThat(asked.get(1)).contains("ONLY the JSON array");
    }

    @Test
    void aPartThatFailsTwiceIsSkippedWithItsReason() {
        List<String> progress = new ArrayList<>();
        Extract.Result r = Extract.run(List.of(COMMIT), (system, user) -> "not json", Set.of(), progress::add);
        assertThat(r.failedParts()).isEqualTo(1);
        assertThat(r.notes()).isEmpty();
        assertThat(progress).anyMatch(l -> l.contains("skipped") && l.contains("no JSON array"));
    }

    @Test
    void longSourcesAreCutIntoPartsAndEveryUnitIsCovered() {
        String line = "A sentence that is about eighty characters long, repeated to make a long document.\n";
        Reader.Unit big = new Reader.Unit(line.repeat(200), "sources", "file:big.md", LocalDate.parse("2026-10-01"));
        List<Extract.Part> parts = Extract.parts(List.of(big, COMMIT));
        assertThat(parts.size()).isGreaterThan(2);
        assertThat(parts).allSatisfy(p -> assertThat(p.text().length()).isLessThan(6500));
        assertThat(parts.get(parts.size() - 1).units()).contains(COMMIT);
    }
}
