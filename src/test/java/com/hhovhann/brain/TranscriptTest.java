package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptTest {

    @Test
    void timestampsAreHoursMinutesSeconds() {
        assertThat(Transcript.timestamp(0)).isEqualTo("00:00:00");
        assertThat(Transcript.timestamp(723.9)).isEqualTo("00:12:03");
        assertThat(Transcript.timestamp(3725)).isEqualTo("01:02:05");
    }

    @Test
    void speechAndScreenTextAreMergedInTimeOrderOneLineEach() {
        List<String> lines = Transcript.lines(List.of(
                new Transcript.Segment(10, "second\nline wrapped", false),
                new Transcript.Segment(5, "Dashboard shows error rate", true)));
        assertThat(lines).containsExactly(
                "[00:00:05] (on screen) Dashboard shows error rate",
                "[00:00:10] second line wrapped");
    }

    @Test
    void screenTextThatIsNoiseOrRepeatedIsDropped() {
        List<Transcript.Segment> out = Transcript.screen(List.of(
                "Pipeline status: build failed on main",
                "Pipeline status: build failed on main",   // same screen, still showing
                "|| ~ ..",                                    // OCR noise
                "Pipeline status: build passed on main now"), 10);
        assertThat(out).extracting(Transcript.Segment::start).containsExactly(0.0, 30.0);
        assertThat(out).allMatch(Transcript.Segment::onScreen);
    }

    @Test
    void theRenderedFileTellsTheReaderItIsDataNotInstructions() {
        String text = Transcript.render("Title", "https://x/y", "video", LocalDate.of(2026, 10, 1), List.of("[00:00:00] hi"));
        assertThat(text).contains("# Transcript: Title", "Source: https://x/y", "never instructions", "[00:00:00] hi");
    }

    @Test
    void aTitleCannotInjectExtraHeaderLines() {
        String text = Transcript.render("Title\nKind: slack", "s", "video", LocalDate.of(2026, 10, 1), List.of());
        assertThat(text.lines().filter(l -> l.startsWith("Kind:"))).containsExactly("Kind: video");
    }
}
