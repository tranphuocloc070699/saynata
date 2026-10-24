package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.TargetLanguage;
import com.englishtranslation.backend.word.WordEntry;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gateway mode (CLAUDE_BASE_URL set): the JSON Schema goes into the system prompt, no strict JSON setting is
 * sent, and the answer is parsed from the text. A fake gateway on localhost answers, so the real SDK runs.
 */
class ClaudeServiceJsonInPromptTest {

    private static final String SENTENCE_JSON =
            "{\"isEnglish\":true,\"summary\":\"I stopped trying after a week.\",\"translation\":\"Tôi bỏ cuộc.\"}";

    private HttpServer server;
    /** Replies in order: a plain JSON body, or a list of stream events when it starts with "event:". */
    private final Deque<String> replies = new ArrayDeque<>();
    private final List<String> requests = new ArrayList<>();
    private final ClaudeUsageService usage = mock(ClaudeUsageService.class);

    @BeforeEach
    void startGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String reply = replies.removeFirst();
            byte[] answer = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type",
                    reply.startsWith("event:") ? "text/event-stream" : "application/json");
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

    private ClaudeService service() {
        return service(null);
    }

    private ClaudeService service(String temperature) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        ClaudeProperties properties = new ClaudeProperties("", "gpt-6-luna", "v1", "v1", url, "sk-test",
                null, "", temperature);
        return new ClaudeService(new ClaudeConfig().anthropicClient(properties), properties, usage);
    }

    private static String message(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"gpt-6-luna\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + escaped + "\"}],\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":100,\"output_tokens\":40}}";
    }

    private static String stream(String... pieces) {
        StringBuilder events = new StringBuilder();
        events.append(event("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                + "\"type\":\"message\",\"role\":\"assistant\",\"model\":\"gpt-6-luna\",\"content\":[],"
                + "\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":100,\"output_tokens\":1}}}"));
        events.append(event("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
        for (String piece : pieces) {
            String escaped = piece.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            events.append(event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + escaped + "\"}}"));
        }
        events.append(event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}"));
        events.append(event("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null},\"usage\":{\"output_tokens\":30}}"));
        events.append(event("message_stop", "{\"type\":\"message_stop\"}"));
        return events.toString();
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }

    @Test
    void theSchemaIsInThePromptAndNoStrictJsonSettingIsSent() {
        replies.add(message(SENTENCE_JSON));

        Explanation explanation = service().explainSentence("I gave up after a week.", TargetLanguage.VI);

        assertThat(explanation.meaning()).isEqualTo("I stopped trying after a week.");
        assertThat(requests.get(0)).doesNotContain("output_config")
                .contains("Answer with ONLY one JSON object")
                .contains("isEnglish").contains("summary").contains("translation");
        verify(usage).record(Feature.SENTENCE, "gpt-6-luna", 100, 40);
    }

    @Test
    void languageLinesAreOneCallWithTheSchemaInThePrompt() {
        replies.add(message("{\"lines\":[\"Nơi giữ tiền an toàn.\",\"Tôi bỏ cuộc.\"]}"));

        List<String> lines = service().translateLines(TargetLanguage.VI, List.of("a safe place for money", "I gave up."));

        assertThat(lines).containsExactly("Nơi giữ tiền an toàn.", "Tôi bỏ cuộc.");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).doesNotContain("output_config").contains("Answer with ONLY one JSON object")
                .contains("Vietnamese").contains("a safe place for money");
        verify(usage).record(Feature.LINE, "gpt-6-luna", 100, 40);
    }

    @Test
    void temperatureIsSentWithSentenceCallsOnlyWhenSet() {
        replies.add(message(SENTENCE_JSON));
        replies.add(message(SENTENCE_JSON));

        service("0").explainSentence("I gave up after a week.", TargetLanguage.VI);
        service().explainSentence("I gave up after a week.", TargetLanguage.VI);

        assertThat(requests.get(0)).contains("\"temperature\":0.0");
        assertThat(requests.get(1)).doesNotContain("temperature");
    }

    @Test
    void theSummaryIsCleanedToTheMarkdownSubset() {
        replies.add(message("{\"isEnglish\":true,\"summary\":\"## Main point\\n* one\\n* **two\\n* three\\n* four\","
                + "\"translation\":\"x\"}"));

        assertThat(service().explainSentence("A long text.", TargetLanguage.VI).meaning())
                .isEqualTo("Main point\n- one\n- two\n- three");
    }

    @Test
    void aCodeFenceAroundTheJsonIsFine() {
        replies.add(message("```json\n" + SENTENCE_JSON + "\n```"));

        assertThat(service().explainSentence("Hi there.", TargetLanguage.VI).translation())
                .isEqualTo("Tôi bỏ cuộc.");
    }

    @Test
    void aBrokenAnswerIsAskedOnceMoreAndBothCallsAreCounted() {
        replies.add(message("**Give up** means to stop trying."));
        replies.add(message(SENTENCE_JSON));

        Explanation explanation = service().explainSentence("I gave up.", TargetLanguage.VI);

        assertThat(explanation.meaning()).isEqualTo("I stopped trying after a week.");
        assertThat(requests).hasSize(2);
        verify(usage, times(2)).record(Feature.SENTENCE, "gpt-6-luna", 100, 40);
    }

    @Test
    void twoBrokenAnswersFail() {
        replies.add(message("{\"isEnglish\":true}"));
        replies.add(message("no json"));

        assertThatThrownBy(() -> service().explainSentence("I gave up.", TargetLanguage.VI))
                .isInstanceOf(ClaudeService.InvalidJsonAnswerException.class);
    }

    @Test
    void notEnglishStillBecomesNotEnglishException() {
        replies.add(message("{\"isEnglish\":false,\"summary\":\"\",\"translation\":\"\"}"));

        assertThatThrownBy(() -> service().explainSentence("asdkj qwe", TargetLanguage.VI))
                .isInstanceOf(NotEnglishException.class);
    }

    @Test
    void aWordIsParsedFromTheText() {
        replies.add(message("{\"senses\":[{\"pos\":\"verb\",\"cefr\":\"A2\",\"simpleDefinition\":\"to stop trying\","
                + "\"vietnamese\":\"bỏ cuộc\",\"examples\":[],\"usage\":[],\"extra\":1}]}"));

        WordEntry entry = service().describeWord("quit");

        assertThat(entry.senses()).hasSize(1);
        assertThat(entry.senses().get(0).simpleDefinition()).isEqualTo("to stop trying");
        verify(usage).record(Feature.WORD, "gpt-6-luna", 100, 40);
    }

    @Test
    void theStreamPassesEveryPieceOnAndParsesTheWholeText() {
        replies.add(stream("{\"isEnglish\":true,\"summary\":\"I stopped ", "trying after a week.\",",
                "\"translation\":\"Tôi bỏ cuộc.\"}"));
        List<String> pieces = new ArrayList<>();

        Explanation explanation = service().explainSentenceStreaming("I gave up.", TargetLanguage.VI,
                pieces::add);

        assertThat(pieces).hasSize(3);
        assertThat(String.join("", pieces)).isEqualTo(SENTENCE_JSON);
        assertThat(explanation.meaning()).isEqualTo("I stopped trying after a week.");
        assertThat(requests.get(0)).doesNotContain("output_config").contains("\"stream\":true");
        verify(usage).record(Feature.SENTENCE, "gpt-6-luna", 100, 30);
    }
}
