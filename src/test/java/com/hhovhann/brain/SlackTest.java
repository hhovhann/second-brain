package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlackTest {

    private HttpServer server;
    private String base;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/conversations.replies", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            queries.add(ex.getRequestURI().getQuery());
            String q = ex.getRequestURI().getQuery();
            String body;
            if (q.contains("channel=CBAD")) {
                body = "{\"ok\":false,\"error\":\"not_in_channel\"}";
            } else if (q.contains("cursor=next")) {
                body = "{\"ok\":true,\"messages\":[{\"ts\":\"1727700120.000200\",\"user\":\"U2\",\"text\":\"Agreed &amp; done\"}],"
                        + "\"response_metadata\":{\"next_cursor\":\"\"}}";
            } else {
                body = "{\"ok\":true,\"messages\":[{\"ts\":\"1727700000.000100\",\"user\":\"U1\","
                        + "\"text\":\"Drop <@U2> the graph db, see <https://x.test/a|the doc>\"}],"
                        + "\"response_metadata\":{\"next_cursor\":\"next\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aMessageLinkYieldsChannelAndTimestamp() {
        Slack.Ref ref = Slack.parse("https://acme.slack.com/archives/C0123ABCD/p1727700000123456");
        assertThat(ref).isEqualTo(new Slack.Ref("C0123ABCD", "1727700000.123456", "1727700000.123456"));
    }

    @Test
    void aReplyLinkNamesItsThreadParent() {
        Slack.Ref ref = Slack.parse(
                "https://acme.slack.com/archives/C0123ABCD/p1727700999123456?thread_ts=1727700000.000100&cid=C0123ABCD");
        assertThat(ref.threadTs()).isEqualTo("1727700000.000100");
        assertThat(ref.ts()).isEqualTo("1727700999.123456");
    }

    @Test
    void anythingThatIsNotAMessageLinkIsRefused() {
        assertThatThrownBy(() -> Slack.parse("https://acme.slack.com/archives/C0123ABCD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markupBecomesPlainText() {
        String text = Slack.readable("Hi <@U2>, <#C1|dev> &lt;3 <https://x.test/a|the doc> <https://y.test>", Map.of("U2", "Sam"));
        assertThat(text).isEqualTo("Hi @Sam, #dev <3 the doc (https://x.test/a) https://y.test");
    }

    @Test
    void aThreadIsFollowedAcrossPagesAndRenderedOneLinePerMessage() throws Exception {
        List<Slack.Message> messages = Slack.fetchThread(HttpClient.newHttpClient(), base, "xoxp-test",
                new Slack.Ref("C0123ABCD", "1727700000.000100", "1727700000.000100"));
        assertThat(messages).hasSize(2);
        List<String> lines = Slack.lines(messages, Map.of("U1", "Ana", "U2", "Sam"));
        assertThat(lines.get(0)).startsWith("[2024-09-30 ").contains("Ana: Drop @Sam the graph db, see the doc (https://x.test/a)");
        assertThat(lines.get(1)).contains("Sam: Agreed & done");
        assertThat(authHeaders).allMatch("Bearer xoxp-test"::equals);
        assertThat(new ArrayList<>(queries).get(1)).contains("cursor=next");
    }

    @Test
    void slackErrorsAreReportedWithAHintAndNeverTheToken() {
        assertThatThrownBy(() -> Slack.fetchThread(HttpClient.newHttpClient(), base, "xoxp-secret-token",
                new Slack.Ref("CBAD", "1727700000.000100", "1727700000.000100")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not_in_channel")
                .hasMessageContaining("member of the channel")
                .hasMessageNotContaining("xoxp-secret-token");
    }

    @Test
    void theTokenGoesOnlyToTheSlackApiHostFixedInCode() {
        assertThat(Slack.API).isEqualTo("https://slack.com/api/");
    }
}
