package com.englishtranslation.backend.claude;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ClaudeService.warmUp (TASK-75): a 1-token call, recorded as usage, with no budget check. */
class ClaudeServiceWarmUpTest {

    private static final ClaudeProperties PROPERTIES = new ClaudeProperties("key", "claude-haiku-5-5", "v6", "v7");

    @Test
    void makesAOneTokenCallAndRecordsItsUsageAsWarmUp() throws Exception {
        Message reply = ObjectMappers.jsonMapper().readValue(
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-haiku-5-5\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}],\"stop_reason\":\"max_tokens\","
                        + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":8,\"output_tokens\":1}}",
                Message.class);
        MessageService messages = mock(MessageService.class);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messages);
        ClaudeUsageService usage = mock(ClaudeUsageService.class);

        new ClaudeService(client, PROPERTIES, usage).warmUp();

        ArgumentCaptor<MessageCreateParams> params = forClass(MessageCreateParams.class);
        verify(messages).create(params.capture());
        assertThat(params.getValue().maxTokens()).isEqualTo(1L);
        verify(usage).record(Feature.WARMUP, "claude-haiku-5-5", 8, 1);
        // No daily cap check: the budget check is a separate call that warmUp never makes.
        verify(usage, never()).budgetReached();
    }
}
