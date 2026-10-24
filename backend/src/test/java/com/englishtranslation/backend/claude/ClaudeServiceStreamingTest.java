package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.TargetLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ClaudeService.explainSentenceStreaming with fake stream events. The events are written as the JSON that
 * the Messages API really sends, so the SDK reads them the same way it reads a real stream.
 */
class ClaudeServiceStreamingTest {

    private static final ClaudeProperties PROPERTIES = new ClaudeProperties("key", "claude-haiku-5-5", "v6", "v7");

    private static String textDelta(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"" + escaped + "\"}}";
    }

    /** A whole stream: start, one text block made of `pieces`, stop. */
    private static List<String> streamOf(String stopReason, String... pieces) {
        List<String> events = new ArrayList<>();
        events.add("{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"claude-haiku-5-5\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":1}}}");
        events.add("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        for (String piece : pieces) {
            events.add(textDelta(piece));
        }
        events.add("{\"type\":\"content_block_stop\",\"index\":0}");
        events.add("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":20}}");
        events.add("{\"type\":\"message_stop\"}");
        return events;
    }

    private static RawMessageStreamEvent parseEvent(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, RawMessageStreamEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad test event: " + json, e);
        }
    }

    private static ClaudeService serviceStreaming(List<String> eventJson) {
        return serviceStreaming(eventJson, org.mockito.Mockito.mock(ClaudeUsageService.class));
    }

    private static ClaudeService serviceStreaming(List<String> eventJson, ClaudeUsageService usage) {
        List<RawMessageStreamEvent> events = eventJson.stream()
                .map(ClaudeServiceStreamingTest::parseEvent)
                .toList();
        StreamResponse<RawMessageStreamEvent> stream = new StreamResponse<>() {
            @Override
            public Stream<RawMessageStreamEvent> stream() {
                return events.stream();
            }

            @Override
            public void close() {
            }
        };
        MessageService messages = mock(MessageService.class);
        when(messages.createStreaming(any(StructuredMessageCreateParams.class))).thenReturn(stream);
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messages);
        return new ClaudeService(client, PROPERTIES, usage);
    }

    @Test
    void everyPieceOfTextIsPassedOnAndTheWholeAnswerIsReturned() {
        ClaudeService service = serviceStreaming(streamOf("end_turn",
                "{\"isEnglish\":true,\"summary\":\"He did not ", "want to leave.\"", "}"));
        List<String> pieces = new ArrayList<>();

        Explanation explanation = service.explainSentenceStreaming("He was reluctant", pieces::add);

        assertThat(pieces).hasSize(3);
        assertThat(pieces.get(0)).startsWith("{\"isEnglish\":true");
        assertThat(explanation.meaning()).isEqualTo("He did not want to leave.");
        assertThat(explanation.words()).isEmpty();
        assertThat(explanation.translation()).isEmpty();
    }

    @Test
    void anAnswerThatIsNotEnglishThrowsLikeTheNormalCall() {
        ClaudeService service = serviceStreaming(streamOf("end_turn",
                "{\"isEnglish\":false,\"summary\":\"\"}"));

        assertThatThrownBy(() -> service.explainSentenceStreaming("asdkj qwe zzxq", piece -> {
        })).isInstanceOf(NotEnglishException.class);
    }

    @Test
    void aRefusalThrowsLikeTheNormalCall() {
        ClaudeService service = serviceStreaming(streamOf("refusal", ""));

        assertThatThrownBy(() -> service.explainSentenceStreaming("anything", piece -> {
        })).isInstanceOf(ClaudeRefusedException.class);
    }

    @Test
    void theTokensClaudeReportsAreRecordedForTheSentenceFeature() {
        ClaudeUsageService usage = org.mockito.Mockito.mock(ClaudeUsageService.class);
        ClaudeService service = serviceStreaming(streamOf("end_turn",
                "{\"isEnglish\":true,\"summary\":\"Hi.\"}"), usage);

        service.explainSentenceStreaming("Hello", piece -> {
        });

        // message_start reported 10 input tokens, message_delta reported 20 output tokens.
        org.mockito.Mockito.verify(usage).record(Feature.SENTENCE, "claude-haiku-5-5", 10, 20);
    }

    @Test
    void aRefusedAnswerIsStillRecordedBecauseItCostMoney() {
        ClaudeUsageService usage = org.mockito.Mockito.mock(ClaudeUsageService.class);
        ClaudeService service = serviceStreaming(streamOf("refusal", ""), usage);

        assertThatThrownBy(() -> service.explainSentenceStreaming("anything", piece -> {
        })).isInstanceOf(ClaudeRefusedException.class);

        org.mockito.Mockito.verify(usage).record(Feature.SENTENCE, "claude-haiku-5-5", 10, 20);
    }
}
