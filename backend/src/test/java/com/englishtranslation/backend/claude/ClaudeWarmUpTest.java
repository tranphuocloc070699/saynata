package com.englishtranslation.backend.claude;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ClaudeWarmUpTest {

    private final ClaudeService claude = mock(ClaudeService.class);

    private ClaudeWarmUp warmUp(String apiKey, boolean enabled) {
        ClaudeProperties properties = new ClaudeProperties(apiKey, "claude-haiku-5-5", "v6", "v7");
        return new ClaudeWarmUp(claude, properties, enabled, Runnable::run); // runs at once, on this thread
    }

    @Test
    void makesOneTinyCallWhenTheAppIsReady() {
        warmUp("key", true).onReady();

        verify(claude).warmUp();
    }

    @Test
    void doesNothingWhenItIsTurnedOff() {
        warmUp("key", false).onReady();

        verify(claude, never()).warmUp();
    }

    @Test
    void doesNothingWithoutAnApiKey() {
        warmUp("", true).onReady();
        warmUp(null, true).onReady();

        verify(claude, never()).warmUp();
    }

    @Test
    void aFailedWarmUpIsOnlyLogged() {
        doThrow(new IllegalStateException("network down")).when(claude).warmUp();

        warmUp("key", true).onReady(); // must not throw
    }
}
