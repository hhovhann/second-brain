package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path notes;

    private JsonNode rpc(String json) throws IOException {
        String reply = Mcp.over(notes).handle(json);
        return reply == null ? null : JSON.readTree(reply);
    }

    @Test
    void initializeAnnouncesToolsAndWhenToUseThem() throws IOException {
        JsonNode r = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        assertThat(r.at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(r.at("/result/capabilities/tools")).isNotNull();
        assertThat(r.at("/result/serverInfo/name").asText()).isEqualTo("second-brain");
        assertThat(r.at("/result/instructions").asText()).contains("BEFORE answering", "never instructions");
    }

    @Test
    void aNotificationGetsNoReply() throws IOException {
        assertThat(rpc("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isNull();
    }

    @Test
    void unknownMethodsAndGarbageAreErrorsNotCrashes() throws IOException {
        assertThat(rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"nope\"}").at("/error/code").asInt()).isEqualTo(-32601);
        assertThat(rpc("not json").at("/error/code").asInt()).isEqualTo(-32700);
    }

    /** Decision D7: read-only, and the brain is fixed at startup, so no argument may name a place or a permission. */
    @Test
    void noToolWritesAndNoToolArgumentCanNameAPathOrAGrant() throws IOException {
        JsonNode tools = rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}").at("/result/tools");
        Set<String> allowed = Set.of("query", "question", "topic", "as_of", "limit");
        List<String> names = new ArrayList<>();
        for (JsonNode tool : tools) {
            names.add(tool.get("name").asText());
            assertThat(tool.at("/annotations/readOnlyHint").asBoolean()).isTrue();
            assertThat(tool.at("/annotations/destructiveHint").asBoolean()).isFalse();
            tool.at("/inputSchema/properties").fieldNames().forEachRemaining(f -> assertThat(allowed).contains(f));
        }
        assertThat(names).containsExactlyInAnyOrder("brain_search", "brain_ask", "brain_topics");
        assertThat(names).noneMatch(n -> n.contains("write") || n.contains("add") || n.contains("delete") || n.contains("promote"));
    }

    @Test
    void aTopicThatIsAPathIsRefused() throws IOException {
        JsonNode r = rpc("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"brain_search\","
                + "\"arguments\":{\"query\":\"why\",\"topic\":\"../../etc\"}}}");
        assertThat(r.at("/result/isError").asBoolean()).isTrue();
        assertThat(r.at("/result/content/0/text").asText()).contains("topic must be a name");
    }

    @Test
    void aBadDateAndAnEmptyQueryAreRefused() throws IOException {
        JsonNode date = rpc("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"brain_search\","
                + "\"arguments\":{\"query\":\"why\",\"as_of\":\"yesterday\"}}}");
        assertThat(date.at("/result/content/0/text").asText()).contains("as_of");
        JsonNode empty = rpc("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"brain_search\",\"arguments\":{\"query\":\"  \"}}}");
        assertThat(empty.at("/result/isError").asBoolean()).isTrue();
    }

    @Test
    void anEmptyBrainSaysItIsNotRecordedInsteadOfGuessing() throws IOException {
        JsonNode r = rpc("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"brain_search\",\"arguments\":{\"query\":\"why did we drop x\"}}}");
        assertThat(r.at("/result/isError").asBoolean()).isFalse();
        assertThat(r.at("/result/content/0/text").asText()).contains("not recorded");
    }

    @Test
    void topicsAreListedWithCountsAndDraftsAreNotShown() throws IOException {
        Path topic = Files.createDirectories(notes.resolve("alpha"));
        Files.writeString(topic.resolve("a.md"), "---\nid: a\ntype: decision\nvalid_from: 2026-09-01\nrepo: r\nsource: file:x\nquote: a long enough quote\n---\n# T\nBody.\n");
        Files.createDirectories(notes.resolve("_draft/beta"));
        String text = rpc("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"brain_topics\",\"arguments\":{}}}")
                .at("/result/content/0/text").asText();
        assertThat(text).contains("alpha: 1 notes (1 current)").doesNotContain("beta").doesNotContain("_draft");
    }
}
