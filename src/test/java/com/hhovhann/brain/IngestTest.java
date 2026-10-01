package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngestTest {

    @Test
    void inputsAreClassifiedByShape() {
        assertThat(Ingest.classify("https://acme.slack.com/archives/C0123ABCD/p1727700000123456")).isEqualTo(Ingest.Kind.SLACK);
        assertThat(Ingest.classify("https://www.youtube.com/watch?v=abc")).isEqualTo(Ingest.Kind.URL);
        assertThat(Ingest.classify("/Users/me/recording.mp4")).isEqualTo(Ingest.Kind.FILE);
    }

    @Test
    void aSlackLookingLinkOnAnotherHostIsAnOrdinaryUrlNotSlack() {
        assertThat(Ingest.classify("https://evil.example/https://acme.slack.com/archives/C0123ABCD/p1727700000123456"))
                .isEqualTo(Ingest.Kind.URL);
    }

    @Test
    void idsAreSafeFolderNames() {
        assertThat(Ingest.idFor(Ingest.Kind.FILE, "/tmp/My Team Call (Oct 3)!.mp4")).isEqualTo("rec-my-team-call-oct-3");
        assertThat(Ingest.idFor(Ingest.Kind.FILE, "../../etc/passwd.mp4")).isEqualTo("rec-passwd");
        assertThat(Ingest.idFor(Ingest.Kind.URL, "https://example.com/v")).matches("video-[0-9a-f]{10}");
        assertThat(Ingest.idFor(Ingest.Kind.SLACK, "https://acme.slack.com/archives/C0123ABCD/p1727700000123456"))
                .isEqualTo("slack-c0123abcd-1727700000123456");
    }
}
