package com.englishtranslation.backend.claude;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Makes one tiny Claude call right after start-up (TASK-75). The first call after a restart used to take 7-9 s,
 * because it had to open the HTTPS connection and the JVM had not run the Claude code yet. This pays that cost
 * in the background, before a person asks for a sentence.
 *
 * It runs on its own thread, so start-up and /actuator/health do not wait for it. If it fails, we only log it:
 * the app works the same, the first sentence is just slower. Turn it off with {@code app.claude.warm-up=false}
 * (the tests do). It is also skipped when there is no API key.
 */
@Component
public class ClaudeWarmUp {

    private static final Logger log = LoggerFactory.getLogger(ClaudeWarmUp.class);

    private final ClaudeService claude;
    private final ClaudeProperties properties;
    private final boolean enabled;
    private final Executor executor;

    @Autowired
    public ClaudeWarmUp(ClaudeService claude, ClaudeProperties properties,
            @Value("${app.claude.warm-up:true}") boolean enabled) {
        this(claude, properties, enabled, backgroundThread());
    }

    /** The executor can be replaced in a test. */
    ClaudeWarmUp(ClaudeService claude, ClaudeProperties properties, boolean enabled, Executor executor) {
        this.claude = claude;
        this.properties = properties;
        this.enabled = enabled;
        this.executor = executor;
    }

    private static Executor backgroundThread() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("claude-warm-up-");
        executor.setDaemon(true); // never keeps the JVM alive
        return executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            log.info("Claude warm-up is off (app.claude.warm-up=false).");
            return;
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("Claude warm-up skipped: no API key.");
            return;
        }
        executor.execute(this::warmUp);
    }

    private void warmUp() {
        long start = System.nanoTime();
        try {
            claude.warmUp();
            log.info("Claude warm-up done in {} ms.", (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("Claude warm-up failed after {} ms (the app still works): {}",
                    (System.nanoTime() - start) / 1_000_000, e.toString());
        }
    }
}
