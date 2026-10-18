package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ClaudeService.writePictureScene (TASK-91): Sonnet 5.5, no temperature, usage saved as ADMIN. */
class ClaudeServiceSceneTest {

    private static final ClaudeProperties PROPERTIES = new ClaudeProperties("key", "claude-haiku-5-5", "v6", "v7");

    private static Message reply(String text, String stopReason) throws Exception {
        return ObjectMappers.jsonMapper().readValue(
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-5-5\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"stop_reason\":\"" + stopReason
                        + "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":900,\"output_tokens\":300}}",
                Message.class);
    }

    @Test
    void asksSonnetWithTheSystemPromptAndNoTemperatureAndRecordsAdminUsage() throws Exception {
        MessageService messages = mock(MessageService.class);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(reply("{}", "end_turn"));
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messages);
        ClaudeUsageService usage = mock(ClaudeUsageService.class);

        String text = new ClaudeService(client, PROPERTIES, usage).writePictureScene("SYSTEM TEXT", "USER TEXT");

        assertThat(text).isEqualTo("{}");
        ArgumentCaptor<MessageCreateParams> params = forClass(MessageCreateParams.class);
        verify(messages).create(params.capture());
        assertThat(params.getValue().model().asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(params.getValue().temperature()).isEmpty();
        assertThat(params.getValue().system().orElseThrow().toString()).contains("SYSTEM TEXT");
        assertThat(params.getValue().messages().get(0).content().toString()).contains("USER TEXT");
        verify(usage).record(Feature.ADMIN, "claude-sonnet-5-5", 900, 300);
    }

    @Test
    void aRefusalIsCountedAndBecomesClaudeRefusedException() throws Exception {
        MessageService messages = mock(MessageService.class);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(reply("no", "refusal"));
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messages);
        ClaudeUsageService usage = mock(ClaudeUsageService.class);

        assertThatThrownBy(() -> new ClaudeService(client, PROPERTIES, usage).writePictureScene("s", "u"))
                .isInstanceOf(ClaudeRefusedException.class);
        verify(usage).record(Feature.ADMIN, "claude-sonnet-5-5", 900, 300);
    }
}
