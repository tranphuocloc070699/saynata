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
        return new ClaudeService(client, PROPERTIES);
    }

    @Test
    void everyPieceOfTextIsPassedOnAndTheWholeAnswerIsReturned() {
        ClaudeService service = serviceStreaming(streamOf("end_turn",
                "{\"isEnglish\":true,\"meaning\":\"He did not ", "want to leave.\",",
                "\"words\":[{\"word\":\"reluctant\",\"partOfSpeech\":\"adjective\",\"meaning\":\"not wanting\"}],",
                "\"translation\":\"Anh ấy không muốn đi.\"}"));
        List<String> pieces = new ArrayList<>();

        Explanation explanation = service.explainSentenceStreaming("He was reluctant", TargetLanguage.VI, pieces::add);

        assertThat(pieces).hasSize(4);
        assertThat(pieces.get(0)).startsWith("{\"isEnglish\":true");
        assertThat(explanation.meaning()).isEqualTo("He did not want to leave.");
        assertThat(explanation.words()).containsExactly(new Explanation.Word("reluctant", "adjective", "not wanting"));
        assertThat(explanation.translation()).isEqualTo("Anh ấy không muốn đi.");
    }

    @Test
    void anAnswerThatIsNotEnglishThrowsLikeTheNormalCall() {
        ClaudeService service = serviceStreaming(streamOf("end_turn",
                "{\"isEnglish\":false,\"meaning\":\"\",\"words\":[],\"translation\":\"\"}"));

        assertThatThrownBy(() -> service.explainSentenceStreaming("asdkj qwe zzxq", TargetLanguage.VI, piece -> {
        })).isInstanceOf(NotEnglishException.class);
    }

    @Test
    void aRefusalThrowsLikeTheNormalCall() {
        ClaudeService service = serviceStreaming(streamOf("refusal", ""));

        assertThatThrownBy(() -> service.explainSentenceStreaming("anything", TargetLanguage.VI, piece -> {
        })).isInstanceOf(ClaudeRefusedException.class);
    }
}
