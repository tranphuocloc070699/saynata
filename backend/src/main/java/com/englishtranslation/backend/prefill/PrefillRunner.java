package com.englishtranslation.backend.prefill;

import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.NotEnglishException;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.PrefillCapReachedException;
import com.englishtranslation.backend.limits.PrefillContext;
import com.englishtranslation.backend.translation.PersonalTextDetector;
import com.englishtranslation.backend.translation.TargetLanguage;
import com.englishtranslation.backend.translation.TranslationResponse;
import com.englishtranslation.backend.translation.TranslationService;
import com.englishtranslation.backend.word.WordResponse;
import com.englishtranslation.backend.word.WordService;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Answers the queued words and phrases before users ask (TASK-83.1), so their answers are already in the shared cache.
 *
 * It calls the same WordService and TranslationService that users reach, so the prompt, the prompt version, the cache
 * key and the SimpleWords retry are exactly the same. One thing is left out on purpose: a prefilled word gets no
 * picture (WordService skips the Pixabay job inside a prefill run). A cached item costs nothing: it is
 * marked done. What is different is the money: the run has no user, so ClaudeGate checks the run cap
 * ({@link PrefillContext}) instead of sign-in and the users' budget, and the calls are saved as PREFILL.
 *
 * Only one run at a time. A run stops when the queue is empty, when {@code limit} items were handled, when the next
 * item might pass the cap, or when Claude keeps failing.
 */
@Service
public class PrefillRunner {

    private static final Logger log = LoggerFactory.getLogger(PrefillRunner.class);

    /** After this many failures in a row Claude is probably down; stop instead of spending the tries of every item. */
    static final int MAX_FAILURES_IN_A_ROW = 5;
    private static final int KEPT_RUNS = 10;
    private static final int MAX_ERROR_LENGTH = 500;

    /** What one run did. {@code stoppedBecause}: queue-empty, limit, cap, failures, interrupted. */
    public record RunSummary(String trigger, Instant startedAt, Instant finishedAt, BigDecimal maxUsd,
            BigDecimal spentUsd, int handled, int done, int cached, int skipped, int failed, String stoppedBecause) {
    }

    private final PrefillItemRepository items;
    private final WordService words;
    private final TranslationService sentences;
    private final PersonalTextDetector personalText;
    private final ClaudeUsageService usage;
    private final PrefillProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Deque<RunSummary> lastRuns = new ArrayDeque<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "prefill");
        thread.setDaemon(true);
        return thread;
    });

    public PrefillRunner(PrefillItemRepository items, WordService words, TranslationService sentences,
            PersonalTextDetector personalText, ClaudeUsageService usage, PrefillProperties properties, Clock clock) {
        this.items = items;
        this.words = words;
        this.sentences = sentences;
        this.personalText = personalText;
        this.usage = usage;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** The finished runs since the server started, newest first. (Older spend is in /api/admin/usage, feature "prefill".) */
    public synchronized List<RunSummary> lastRuns() {
        return new ArrayList<>(lastRuns);
    }

    /**
     * Starts a run on its own thread and returns at once. Throws 409 {@code prefill_running} when one is running.
     * {@code maxUsd} or {@code limit} may be null (then: the configured cap, and no item limit).
     */
    public void startInBackground(String trigger, BigDecimal maxUsd, Integer limit) {
        claimOrThrow();
        try {
            background.execute(() -> runClaimed(trigger, maxUsd, limit));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    /** Runs on the calling thread and returns what happened. Throws 409 {@code prefill_running} when one is running. */
    public RunSummary runNow(String trigger, BigDecimal maxUsd, Integer limit) {
        claimOrThrow();
        return runClaimed(trigger, maxUsd, limit);
    }

    private void claimOrThrow() {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "prefill_running", "A prefill run is already running.");
        }
    }

    private RunSummary runClaimed(String trigger, BigDecimal maxUsd, Integer limit) {
        try {
            BigDecimal cap = maxUsd == null ? properties.maxUsdPerRun() : maxUsd;
            Instant startedAt = clock.instant();
            Counts counts = new Counts();
            String reason = "queue-empty";
            try {
                PrefillContext.Run run = new PrefillContext.Run(startedAt, cap);
                String[] result = new String[1];
                PrefillContext.runAs(run, () -> result[0] = loop(run, limit, counts));
                reason = result[0];
            } catch (RuntimeException e) {
                log.error("Prefill run stopped by an unexpected error", e);
                reason = "error";
            }
            RunSummary summary = new RunSummary(trigger, startedAt, clock.instant(), cap,
                    usage.prefillSpentSince(startedAt), counts.handled, counts.done, counts.cached, counts.skipped,
                    counts.failed, reason);
            remember(summary);
            log.info("Prefill run ({}) finished: {}", trigger, summary);
            return summary;
        } finally {
            running.set(false);
        }
    }

    private static final class Counts {
        int handled;
        int done;
        int cached;
        int skipped;
        int failed;
    }

    /** Handles items until a stop rule applies. Returns why it stopped. Runs inside PrefillContext. */
    private String loop(PrefillContext.Run run, Integer limit, Counts counts) {
        BigDecimal mostPerItem = BigDecimal.ZERO;
        int failuresInARow = 0;
        while (limit == null || counts.handled < limit) {
            // Stop before an item that could pass the cap: the real spend so far plus the dearest item so far.
            BigDecimal spent = usage.prefillSpentSince(run.startedAt());
            if (spent.compareTo(run.maxUsd()) >= 0 || spent.add(mostPerItem).compareTo(run.maxUsd()) > 0) {
                return "cap";
            }
            Optional<PrefillItem> next = items.next(properties.maxAttempts(), run.startedAt(), PageRequest.of(0, 1))
                    .stream().findFirst();
            if (next.isEmpty()) {
                return "queue-empty";
            }
            PrefillItem item = next.get();

            Outcome outcome;
            try {
                outcome = handle(item);
            } catch (PrefillCapReachedException e) {
                return "cap"; // the item stays pending for the next run
            }
            counts.handled++;
            switch (outcome) {
                case DONE_NEW -> counts.done++;
                case DONE_CACHED -> counts.cached++;
                case SKIPPED -> counts.skipped++;
                case FAILED -> counts.failed++;
            }
            failuresInARow = outcome == Outcome.FAILED ? failuresInARow + 1 : 0;
            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                return "failures";
            }
            BigDecimal after = usage.prefillSpentSince(run.startedAt());
            mostPerItem = mostPerItem.max(after.subtract(spent));
            if (outcome == Outcome.DONE_NEW && !pause()) {
                return "interrupted";
            }
        }
        return "limit";
    }

    private enum Outcome {
        DONE_NEW, DONE_CACHED, SKIPPED, FAILED
    }

    /** Answers one item and saves its new status. Only the cap exception goes out; every other error is saved on the item. */
    private Outcome handle(PrefillItem item) {
        try {
            Outcome outcome = PrefillItem.WORD.equals(item.getKind()) ? answerWord(item) : answerSentence(item);
            if (outcome == Outcome.SKIPPED) {
                return outcome; // answerX already saved the skip reason
            }
            save(item, PrefillItem.DONE, null);
            return outcome;
        } catch (PrefillCapReachedException e) {
            throw e;
        } catch (NotEnglishException e) {
            save(item, PrefillItem.SKIPPED, "Claude said this is not English");
            return Outcome.SKIPPED;
        } catch (RuntimeException e) {
            String error = e.toString();
            item.fail(error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error, clock.instant());
            items.save(item);
            log.warn("Prefill item {} '{}' failed (attempt {}): {}", item.getId(), item.getText(), item.getAttempts(), error);
            return Outcome.FAILED;
        }
    }

    private Outcome answerWord(PrefillItem item) {
        WordResponse answer = words.lookUp(item.getText());
        if (answer.entry() == null || answer.entry().senses().isEmpty()) {
            // Not on the English word list: WordService answers "no senses" and saves nothing.
            save(item, PrefillItem.SKIPPED, "not an English word (not on the word list)");
            return Outcome.SKIPPED;
        }
        return answer.cached() ? Outcome.DONE_CACHED : Outcome.DONE_NEW;
    }

    private Outcome answerSentence(PrefillItem item) {
        if (personalText.looksPersonal(item.getText())) {
            // TranslationService would explain it but not save it; there is no point in paying for that.
            save(item, PrefillItem.SKIPPED, "looks like personal text, never saved");
            return Outcome.SKIPPED;
        }
        Optional<TargetLanguage> language = languageOf(item.getTargetLang());
        if (language.isEmpty()) {
            save(item, PrefillItem.SKIPPED, "unknown target language '" + item.getTargetLang() + "'");
            return Outcome.SKIPPED;
        }
        TranslationResponse answer = sentences.translate(item.getText(), language.get());
        return answer.cached() ? Outcome.DONE_CACHED : Outcome.DONE_NEW;
    }

    private static Optional<TargetLanguage> languageOf(String code) {
        for (TargetLanguage language : TargetLanguage.values()) {
            if (language.code().equals(code)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    private void save(PrefillItem item, String status, String note) {
        item.finish(status, note, clock.instant());
        items.save(item);
    }

    /** The pause after a new word. False if the thread was interrupted (the server is stopping). */
    private boolean pause() {
        long millis = properties.delay().toMillis();
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private synchronized void remember(RunSummary summary) {
        lastRuns.addFirst(summary);
        while (lastRuns.size() > KEPT_RUNS) {
            lastRuns.removeLast();
        }
    }

    @PreDestroy
    void stop() {
        background.shutdownNow();
    }
}
