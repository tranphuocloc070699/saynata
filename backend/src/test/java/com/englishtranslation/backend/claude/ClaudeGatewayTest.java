package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.anthropic.client.AnthropicClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An Anthropic-compatible gateway (CLAUDE_BASE_URL + CLAUDE_AUTH_TOKEN): the client calls its URL with a Bearer
 * token, the scene model comes from the settings, and a blank effort is not sent (older models refuse it).
 */
class ClaudeGatewayTest {

    private static final String REPLY = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
            + "\"model\":\"claude-haiku-4-5\",\"content\":[{\"type\":\"text\",\"text\":\"{}\"}],"
            + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":5,\"output_tokens\":1}}";

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void startGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = REPLY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopGateway() {
        server.stop(0);
    }

    private ClaudeProperties gateway(String sceneModel, String effort) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        return new ClaudeProperties("", "claude-haiku-4-5", "v1", "v1", url, "sk-gateway", sceneModel, effort, null);
    }

    @Test
    void callsTheGatewayUrlWithABearerToken() {
        ClaudeProperties properties = gateway("claude-opus-5-5", "");
        AnthropicClient client = new ClaudeConfig().anthropicClient(properties);

        new ClaudeService(client, properties, mock(ClaudeUsageService.class)).warmUp();

        assertThat(path.get()).isEqualTo("/api/v1/messages");
        assertThat(authorization.get()).isEqualTo("Bearer sk-gateway");
        assertThat(apiKeyHeader.get()).isNull();
        assertThat(body.get()).contains("\"model\":\"claude-haiku-4-5\"");
    }

    @Test
    void theSceneModelComesFromTheSettings() {
        ClaudeProperties properties = gateway("claude-opus-5-5", "");
        AnthropicClient client = new ClaudeConfig().anthropicClient(properties);

        new ClaudeService(client, properties, mock(ClaudeUsageService.class)).writePictureScene("s", "u");

        assertThat(body.get()).contains("\"model\":\"claude-opus-5-5\"");
    }

    @Test
    void aBlankEffortIsNotSentAndADefaultSceneModelIsUsed() {
        ClaudeProperties properties = gateway(" ", "");

        assertThat(properties.effortLevel()).isEmpty();
        assertThat(properties.sceneModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(gateway(null, "low").effortLevel()).isPresent();
    }
}
