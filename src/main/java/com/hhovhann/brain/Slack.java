package com.hhovhann.brain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one Slack thread from a message link, with a token the user supplies.
 *
 * <p>Slack has no public way to read a link; the Web API with a token is the supported one.
 * The token is only ever sent to {@link #API}, never to the host written in the link, so a
 * crafted link cannot make the tool send the token elsewhere.
 */
final class Slack {

    static final String API = "https://slack.com/api/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern LINK = Pattern.compile(
            "https://[a-z0-9-]+\\.slack\\.com/archives/([A-Z0-9]{9,12})/p(\\d{10})(\\d{6})(?:\\?(.*))?");
    private static final Pattern THREAD_TS = Pattern.compile("(?:^|&)thread_ts=(\\d{10}\\.\\d{6})(?:&|$)");
    private static final int MAX_MESSAGES = 1000;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** {@code ts} is the message the link points at; {@code threadTs} the thread's parent. */
    record Ref(String channel, String ts, String threadTs) {}

    record Message(String ts, String user, String text) {}

    private Slack() {}

    static boolean isLink(String text) {
        return LINK.matcher(text).matches();
    }

    static Ref parse(String link) {
        Matcher m = LINK.matcher(link);
        if (!m.matches()) {
            throw new IllegalArgumentException("not a Slack message link: " + link);
        }
        String ts = m.group(2) + "." + m.group(3);
        Matcher thread = m.group(4) == null ? null : THREAD_TS.matcher(m.group(4));
        return new Ref(m.group(1), ts, thread != null && thread.find() ? thread.group(1) : ts);
    }

    static List<Message> fetchThread(HttpClient http, String apiBase, String token, Ref ref)
            throws IOException, InterruptedException {
        List<Message> out = new ArrayList<>();
        String cursor = null;
        do {
            String query = "conversations.replies?channel=" + enc(ref.channel()) + "&ts=" + enc(ref.threadTs())
                    + "&limit=200" + (cursor == null ? "" : "&cursor=" + enc(cursor));
            JsonNode body = get(http, apiBase + query, token);
            for (JsonNode m : body.path("messages")) {
                out.add(new Message(m.path("ts").asText(), m.path("user").asText(m.path("username").asText("unknown")),
                        m.path("text").asText()));
            }
            cursor = body.path("response_metadata").path("next_cursor").asText("");
            if (cursor.isBlank()) {
                cursor = null;
            }
        } while (cursor != null && out.size() < MAX_MESSAGES);
        return out;
    }

    /** Best effort: a display name for each user id, or the id itself if Slack will not say. */
    static Map<String, String> names(HttpClient http, String apiBase, String token, List<Message> messages) {
        Map<String, String> names = new HashMap<>();
        for (Message m : messages) {
            names.computeIfAbsent(m.user(), id -> {
                try {
                    JsonNode user = get(http, apiBase + "users.info?user=" + enc(id), token).path("user");
                    for (String field : List.of("display_name", "real_name")) {
                        String value = user.path("profile").path(field).asText("");
                        if (!value.isBlank()) {
                            return value;
                        }
                    }
                    return user.path("name").asText(id);
                } catch (IOException | RuntimeException e) {
                    return id;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return id;
                }
            });
        }
        return names;
    }

    static List<String> lines(List<Message> messages, Map<String, String> names) {
        List<String> out = new ArrayList<>();
        for (Message m : messages) {
            String when = WHEN.format(Instant.ofEpochSecond(Long.parseLong(m.ts().substring(0, m.ts().indexOf('.')))));
            out.add("[" + when + "] " + names.getOrDefault(m.user(), m.user()) + ": "
                    + Transcript.oneLine(readable(m.text(), names)));
        }
        return out;
    }

    /** Slack's markup to plain text: mentions, channels, links, entities. */
    static String readable(String text, Map<String, String> names) {
        String out = Pattern.compile("<@([UW][A-Z0-9]+)(?:\\|[^>]*)?>").matcher(text)
                .replaceAll(r -> Matcher.quoteReplacement("@" + names.getOrDefault(r.group(1), r.group(1))));
        out = out.replaceAll("<#[A-Z0-9]+\\|([^>]+)>", "#$1");
        out = Pattern.compile("<(https?://[^|>]+)\\|([^>]+)>").matcher(out)
                .replaceAll(r -> Matcher.quoteReplacement(r.group(2) + " (" + r.group(1) + ")"));
        out = out.replaceAll("<(https?://[^>]+)>", "$1");
        return out.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static JsonNode get(HttpClient http, String url, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) {
            throw new IOException("Slack rate limit; retry after " + response.headers().firstValue("Retry-After").orElse("a minute") + "s");
        }
        JsonNode body = JSON.readTree(response.body());
        if (!body.path("ok").asBoolean(false)) {
            throw new IOException("Slack said: " + body.path("error").asText("unknown error") + hint(body.path("error").asText("")));
        }
        return body;
    }

    private static String hint(String error) {
        return switch (error) {
            case "not_in_channel", "channel_not_found" -> " (the token's user or bot must be a member of the channel)";
            case "missing_scope" -> " (the token needs channels:history, plus groups:history for private channels)";
            case "invalid_auth", "not_authed", "token_revoked" -> " (check SLACK_TOKEN)";
            default -> "";
        };
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
