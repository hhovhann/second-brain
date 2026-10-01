package com.hhovhann.brain;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Checks what the tool depends on, and proves the Java 27 features it plans to use
 * actually work together: a scoped value read from parallel virtual threads.
 *
 * <p>Exit code is non-zero when something is unusable, so this works as a CI gate.
 */
final class Doctor {

    /** Who is asking. Bound once at the entry point; never a method argument. */
    static final ScopedValue<String> READER = ScopedValue.newInstance();

    private Doctor() {}

    static int run(PrintStream out) {
        int failures = 0;
        out.printf("java        %s%n", Runtime.version());

        try {
            out.printf("concurrency %s%n", concurrencyCheck());
        } catch (Exception | LinkageError e) {
            out.printf("concurrency FAIL %s%n", e);
            failures++;
        }

        String baseUrl = env("BRAIN_LLM_BASE_URL", "http://localhost:1234/v1");
        String embeddingModel = env("BRAIN_EMBEDDING_MODEL", "text-embedding-nomic-embed-text-v1.5");
        try {
            int dimensions = embed(baseUrl, embeddingModel);
            out.printf("model       OK  %s at %s (%d dimensions)%n", embeddingModel, baseUrl, dimensions);
        } catch (RuntimeException e) {
            // A missing model server is a warning, not a failure: capture and ask need it,
            // but the skeleton and the unit tests do not.
            out.printf("model       WARN %s at %s — %s%n", embeddingModel, baseUrl, e.getMessage());
        }
        return failures == 0 ? 0 : 1;
    }

    /** Two parallel branches must both see the reader bound outside them. */
    static String concurrencyCheck() throws Exception {
        List<String> seen = ScopedValue.where(READER, "doctor")
                .call(() -> Parallel.all(List.of(
                        () -> READER.get() + "/branch-1 on " + Thread.currentThread().isVirtual(),
                        () -> READER.get() + "/branch-2 on " + Thread.currentThread().isVirtual())));
        boolean ok = seen.stream().allMatch(s -> s.startsWith("doctor/") && s.endsWith("true"));
        if (!ok) {
            throw new IllegalStateException("scoped value or virtual thread missing in a branch: " + seen);
        }
        return "OK  scoped value reaches parallel virtual threads " + seen;
    }

    private static int embed(String baseUrl, String model) {
        // HTTP/1.1, explicitly: local OpenAI-compatible servers (LM Studio, Ollama) do not
        // answer the JDK client's default h2c upgrade, and every call hangs until timeout.
        var embeddings = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new JdkHttpClientBuilder()
                        .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)))
                .baseUrl(baseUrl)
                .apiKey(env("BRAIN_LLM_API_KEY", "lm-studio"))
                .modelName(model)
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build();
        return embeddings.embed(TextSegment.from("doctor")).content().dimension();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
