package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.claude.ClaudeService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A real HTTP call to a real Tomcat (TASK-65). It proves two things MockMvc cannot:
 * the meaning event reaches the client while Claude is still working (nothing is buffered),
 * and Vietnamese text arrives as UTF-8.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.claude.api-key=test-key")
class TranslationStreamingHttpTest {

    @Value("${local.server.port}")
    int port;

    @MockitoBean
    ClaudeService claude;

    @Test
    void theMeaningArrivesBeforeClaudeHasFinished() throws Exception {
        CountDownLatch clientSawMeaning = new CountDownLatch(1);
        when(claude.explainSentenceStreaming(eq("Real http streaming test"), eq(TargetLanguage.VI), any()))
                .thenAnswer(call -> {
                    Consumer<String> onText = call.getArgument(2);
                    onText.accept("{\"isEnglish\":true,\"summary\":\"Xin chào, đây là nghĩa.\",\"tra");
                    // Claude is "still working" until the client has read the meaning event.
                    boolean seen = clientSawMeaning.await(10, TimeUnit.SECONDS);
                    assertThat(seen).as("the client read the meaning before Claude finished").isTrue();
                    return new Explanation("Xin chào, đây là nghĩa.", List.of(), "Tiếng Việt");
                });

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/translations/stream"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Real http streaming test\",\"targetLang\":\"vi\"}"))
                .build();
        HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(response.headers().firstValue("X-Accel-Buffering")).contains("no");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            // The whole meaning came in one piece, so there is one delta first (TASK-73), then the full meaning.
            assertThat(reader.readLine()).isEqualTo("event:meaning-delta");
            assertThat(reader.readLine()).isEqualTo("data:{\"text\":\"Xin chào, đây là nghĩa.\"}");
            assertThat(reader.readLine()).isEmpty();
            assertThat(reader.readLine()).isEqualTo("event:meaning");
            assertThat(reader.readLine()).isEqualTo("data:{\"meaning\":\"Xin chào, đây là nghĩa.\"}");
            clientSawMeaning.countDown();

            String rest = reader.lines().reduce("", (all, line) -> all + line + "\n");
            assertThat(rest).contains("event:done").contains("\"translation\":\"Tiếng Việt\"");
        }
    }
}
