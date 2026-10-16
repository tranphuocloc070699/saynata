package com.englishtranslation.backend.translation;

import com.anthropic.errors.AnthropicServiceException;
import com.englishtranslation.backend.claude.ClaudeRefusedException;
import com.englishtranslation.backend.claude.NotEnglishException;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.CallerContext;
import com.englishtranslation.backend.prefill.SentenceWordQueue;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private static final Logger log = LoggerFactory.getLogger(TranslationController.class);

    /** A Claude answer takes a few seconds (rarely 10+). One minute is a safe upper limit. */
    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final TranslationService service;
    private final JsonMapper jsonMapper;
    /** The stream is written from another thread, so the request thread is free while Claude works. */
    private final AsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("sentence-stream-");

    /** After the answer is sent, remembers the sentence's unknown words for the nightly prefill job (TASK-83.8). */
    private final SentenceWordQueue sentenceWords;

    public TranslationController(TranslationService service, JsonMapper jsonMapper, SentenceWordQueue sentenceWords) {
        this.service = service;
        this.jsonMapper = jsonMapper;
        this.sentenceWords = sentenceWords;
    }

    @PostMapping
    public TranslationResponse translate(@Valid @RequestBody TranslationRequest request) {
        TranslationResponse response = service.translate(request.text(), request.targetLang());
        // Hands the text to a worker thread and returns at once, so the response time does not change.
        sentenceWords.offer(request.text());
        return response;
    }

    /**
     * The same answer as {@link #translate}, as a stream of server-sent events (TASK-65):
     * <ul>
     * <li>{@code meaning-delta}: {@code {"text": "..."}}, the new characters of the meaning while Claude writes it,
     * about every 50 ms (TASK-73). Old apps ignore events they do not know;</li>
     * <li>{@code meaning}: {@code {"meaning": "..."}} as soon as Claude has written the meaning (cache misses only);</li>
     * <li>{@code done}: the full {@link TranslationResponse}, the same JSON as the old endpoint;</li>
     * <li>{@code error}: {@code {"status": 422, "message": "..."}} when the answer cannot be made. Sign-in, rate
     * limit and daily budget errors also have a {@code code}: sign_in_required, rate_limited, daily_limit_reached.</li>
     * </ul>
     * A cache hit sends only {@code done}. The old endpoint stays for old app versions.
     * The headers tell proxies (Caddy, nginx) not to hold the events back.
     */
    // The charset is written out on purpose: Vietnamese, Japanese... must reach the app as UTF-8.
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public ResponseEntity<SseEmitter> stream(@Valid @RequestBody TranslationRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        // The stream runs on another thread, which does not know who is asking. Hand the user over.
        CallerContext.Caller caller = CallerContext.current().orElse(null);
        executor.execute(() -> CallerContext.runAs(caller, () -> runStream(request, emitter)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private void runStream(TranslationRequest request, SseEmitter emitter) {
        try {
            TranslationResponse response = service.translateStreaming(request.text(), request.targetLang(),
                    delta -> send(emitter, "meaning-delta", Map.of("text", delta)),
                    meaning -> send(emitter, "meaning", Map.of("meaning", meaning)));
            send(emitter, "done", response);
            sentenceWords.offer(request.text());
        } catch (Exception e) {
            send(emitter, "error", describe(e));
        } finally {
            emitter.complete();
        }
    }

    /** What the app can show, and the HTTP status the old endpoint would have used. */
    private static StreamError describe(Exception e) {
        if (e instanceof ApiException apiException) {
            return new StreamError(apiException.status().value(), apiException.getMessage(), apiException.code());
        }
        if (e instanceof NotEnglishException || e instanceof ClaudeRefusedException) {
            return new StreamError(422, e.getMessage(), null);
        }
        if (e instanceof AnthropicServiceException) {
            log.warn("Claude API error while streaming: {}", e.getMessage());
        } else {
            log.error("Streaming a sentence failed", e);
        }
        return new StreamError(502, "The translation service is not available. Try again later.", null);
    }

    /**
     * Never throws. If the app went away, the answer is still saved to the cache by the service,
     * so a failed send must not stop the Claude call.
     */
    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(jsonMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("Could not send the {} event (client gone?): {}", eventName, e.toString());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record StreamError(int status, String message, String code) {
    }
}
