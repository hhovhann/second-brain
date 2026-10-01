package com.hhovhann.brain;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.net.http.HttpClient;
import java.time.Duration;

/** The one place the model endpoint is configured: any OpenAI-compatible server. */
final class Models {

    private Models() {}

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * HTTP/1.1, explicitly: local OpenAI-compatible servers (LM Studio, Ollama) do not
     * answer the JDK client's default h2c upgrade, and every call hangs until timeout.
     */
    private static JdkHttpClientBuilder http() {
        return new JdkHttpClientBuilder()
                .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1));
    }

    static EmbeddingModel embeddings() {
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(http())
                .baseUrl(env("BRAIN_LLM_BASE_URL", "http://localhost:1234/v1"))
                .apiKey(env("BRAIN_LLM_API_KEY", "lm-studio"))
                .modelName(env("BRAIN_EMBEDDING_MODEL", "text-embedding-nomic-embed-text-v1.5"))
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
    }

    /** The model that proposes notes. Defaults to the answer model; set BRAIN_EXTRACT_MODEL to use a smaller, faster one. */
    static ChatModel extractor() {
        return chatNamed(env("BRAIN_EXTRACT_MODEL", env("BRAIN_CHAT_MODEL", "qwen/qwen3-14b")));
    }

    static ChatModel chat() {
        return chatNamed(env("BRAIN_CHAT_MODEL", "qwen/qwen3-14b"));
    }

    private static ChatModel chatNamed(String model) {
        return OpenAiChatModel.builder()
                .httpClientBuilder(http())
                .baseUrl(env("BRAIN_LLM_BASE_URL", "http://localhost:1234/v1"))
                .apiKey(env("BRAIN_LLM_API_KEY", "lm-studio"))
                .modelName(model)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(180))
                .maxRetries(0)
                .build();
    }
}
