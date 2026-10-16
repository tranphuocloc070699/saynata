package com.englishtranslation.backend.prefill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.CallerContext;
import com.englishtranslation.backend.limits.PrefillContext;
import com.englishtranslation.backend.media.BackendWordMediaService;
import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.translation.TargetLanguage;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookupRepository;
import com.englishtranslation.backend.word.WordService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The prefill run, with a fake Claude (TASK-83.1). The fake saves a usage row like the real one does, so the
 * costs are real: 1,000,000 input tokens of claude-haiku-5-5 cost $0.10.
 * Sign-in is required here, to show that a prefill run needs no user.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true",
        "app.prefill.delay=0s",
        "app.claude.daily-budget-usd=0.05"
})
class PrefillRunnerTest {

    static final WordEntry ENTRY = new WordEntry(List.of(new WordEntry.Sense(
            "noun", "B1", "a thing", "một thứ", List.of(), List.of())));
    static final Explanation EXPLANATION = new Explanation("A greeting.", List.of(), "Xin chào");

    @Autowired
    PrefillRunner runner;
    @Autowired
    PrefillItemRepository queue;
    @Autowired
    WordService wordService;
    @Autowired
    WordLookupRepository words;
    @Autowired
    SentenceTranslationRepository sentences;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    ClaudeProperties properties;
    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    BackendWordMediaService media;

    @BeforeEach
    void clean() {
        queue.deleteAll();
        words.deleteAll();
        sentences.deleteAll();
        usageRows.deleteAll();
    }

    /** Makes the fake Claude answer a word and save what a call cost, like ClaudeService does. */
    private void claudeAnswersWords(long inputTokens) {
        when(claude.describeWord(anyString())).thenAnswer(call -> {
            usage.record(Feature.WORD, properties.model(), inputTokens, 0);
            return ENTRY;
        });
    }

    private void queueWord(String text, int priority) {
        queue.save(new PrefillItem("word", text, "", "test", priority, Instant.now()));
    }

    private PrefillItem item(String text) {
        return queue.findAll().stream().filter(i -> i.getText().equals(text)).findFirst().orElseThrow();
    }

    @Test
    void aPrefilledWordGetsNoPicture() {
        claudeAnswersWords(1_000);
        queueWord("lantern", 5);

        runner.runNow("test", null, null);

        assertThat(item("lantern").getStatus()).isEqualTo("done");
        verifyNoInteractions(media); // no Pixabay call, no claim row, no ffmpeg
    }

    @Test
    void aSignedInUsersNewWordStillGetsAPicture() {
        claudeAnswersWords(1_000);

        CallerContext.runAs(new CallerContext.Caller(1L, "127.0.0.1"), () -> wordService.lookUp("lantern"));

        verify(media).fetchInBackground(anyLong(), eq("lantern"), any());
    }

    @Test
    void newWordAndPhraseAreAnsweredWithoutAUserAndSavedAsPrefill() {
        claudeAnswersWords(1_000_000); // $0.10
        when(claude.explainSentence(anyString(), eq(TargetLanguage.VI))).thenAnswer(call -> {
            usage.record(Feature.SENTENCE, properties.model(), 1_000_000, 0);
            return EXPLANATION;
        });
        queueWord("lantern", 5);
        queue.save(new PrefillItem("sentence", "give up", "vi", "test", 1, Instant.now()));

        PrefillRunner.RunSummary summary = runner.runNow("test", new BigDecimal("5"), null);

        assertThat(summary.done()).isEqualTo(2);
        assertThat(summary.stoppedBecause()).isEqualTo("queue-empty");
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        assertThat(item("give up").getStatus()).isEqualTo("done");
        assertThat(words.findAll()).hasSize(1);
        assertThat(sentences.findAll()).hasSize(1);
        assertThat(usageRows.findAll()).hasSize(2).allMatch(row -> row.getFeature() == Feature.PREFILL);
        assertThat(summary.spentUsd()).isEqualByComparingTo("0.20");
    }

    @Test
    void prefillDoesNotUseTheUsersDailyBudget() {
        claudeAnswersWords(1_000_000); // $0.10 per call, the users' budget in this test is $0.05
        queueWord("lantern", 0);
        queueWord("hesitant", 0);

        PrefillRunner.RunSummary summary = runner.runNow("test", new BigDecimal("5"), null);

        // Both words were answered although a user's call of this size would have reached the $0.05 budget.
        assertThat(summary.done()).isEqualTo(2);
        assertThat(usage.spentToday()).isEqualByComparingTo("0");
        assertThat(usage.budgetReached()).isFalse();
        assertThat(usage.prefillSpentSince(Instant.now().minusSeconds(3600))).isEqualByComparingTo("0.20");
        assertThat(usage.report(1).todayUsd()).isEqualByComparingTo("0");
        assertThat(usage.report(1).prefillTodayUsd()).isEqualByComparingTo("0.20");
        assertThat(usage.report(1).days().get(0).byFeature()).containsKey("prefill");
    }

    @Test
    void prefillStillRunsWhenTheUsersBudgetIsUsedUp() {
        usage.record(Feature.WORD, properties.model(), 10_000_000, 0); // $1.00 by users
        assertThat(usage.budgetReached()).isTrue();
        claudeAnswersWords(1_000_000);
        queueWord("lantern", 0);

        PrefillRunner.RunSummary summary = runner.runNow("test", new BigDecimal("5"), null);

        assertThat(summary.done()).isEqualTo(1);
    }

    @Test
    void anAlreadyCachedWordCostsNothingAndIsMarkedDone() {
        when(claude.describeWord("lantern")).thenReturn(ENTRY);
        // A user cached it earlier (here through the prefill door, because this test class requires sign-in).
        PrefillContext.runAs(new PrefillContext.Run(Instant.now(), BigDecimal.ONE), () -> wordService.lookUp("lantern"));
        queueWord("lantern", 0);

        PrefillRunner.RunSummary summary = runner.runNow("test", null, null);

        assertThat(summary.cached()).isEqualTo(1);
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        verify(claude, times(1)).describeWord("lantern"); // only the user's call
        assertThat(usageRows.findAll()).isEmpty();
    }

    @Test
    void aWordNotOnTheWordListAndPersonalTextAreSkippedWithoutClaude() {
        queueWord("qwzxv", 0);
        queue.save(new PrefillItem("sentence", "write to me at jane@example.com", "vi", "test", 0, Instant.now()));

        PrefillRunner.RunSummary summary = runner.runNow("test", null, null);

        assertThat(summary.skipped()).isEqualTo(2);
        assertThat(item("qwzxv").getStatus()).isEqualTo("skipped");
        assertThat(item("write to me at jane@example.com").getStatus()).isEqualTo("skipped");
        verify(claude, never()).describeWord(anyString());
        verify(claude, never()).explainSentence(anyString(), any());
        assertThat(sentences.findAll()).isEmpty(); // personal text is never saved
    }

    @Test
    void theRunStopsBeforeAnItemWouldPassTheCap() {
        claudeAnswersWords(1_000_000); // $0.10 per word
        queueWord("lantern", 5);
        queueWord("hesitant", 4);
        queueWord("tremendous", 3);
        queueWord("pebble", 2);

        PrefillRunner.RunSummary summary = runner.runNow("test", new BigDecimal("0.25"), null);

        // $0.10 + $0.10 = $0.20 spent; a third word could cost $0.10 more, which passes $0.25.
        assertThat(summary.stoppedBecause()).isEqualTo("cap");
        assertThat(summary.done()).isEqualTo(2);
        assertThat(summary.spentUsd()).isEqualByComparingTo("0.20");
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        assertThat(item("hesitant").getStatus()).isEqualTo("done");
        assertThat(item("tremendous").getStatus()).isEqualTo("pending");
        assertThat(item("pebble").getStatus()).isEqualTo("pending");
        verify(claude, times(2)).describeWord(anyString());
    }

    @Test
    void higherPriorityRunsFirstAndLimitStopsTheRun() {
        claudeAnswersWords(1000);
        queueWord("pebble", 1);
        queueWord("lantern", 9);

        PrefillRunner.RunSummary summary = runner.runNow("test", null, 1);

        assertThat(summary.stoppedBecause()).isEqualTo("limit");
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        assertThat(item("pebble").getStatus()).isEqualTo("pending");
    }

    @Test
    void aFailedItemIsRetriedByLaterRunsUntilItHasNoTriesLeft() {
        when(claude.describeWord("lantern")).thenThrow(new IllegalStateException("Claude is down"));
        queueWord("lantern", 0);

        // One run tries a failing item only once.
        PrefillRunner.RunSummary first = runner.runNow("test", null, null);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(item("lantern").getStatus()).isEqualTo("failed");
        assertThat(item("lantern").getAttempts()).isEqualTo(1);
        assertThat(item("lantern").getLastError()).contains("Claude is down");
        verify(claude, times(1)).describeWord("lantern");

        runner.runNow("test", null, null);
        runner.runNow("test", null, null);
        assertThat(item("lantern").getAttempts()).isEqualTo(3);

        // Three failures is the maximum: the next run leaves it alone.
        runner.runNow("test", null, null);
        assertThat(item("lantern").getAttempts()).isEqualTo(3);
        verify(claude, times(3)).describeWord("lantern");
    }

    @Test
    void aFailedItemCanSucceedInALaterRun() {
        when(claude.describeWord("lantern")).thenThrow(new IllegalStateException("Claude is down"))
                .thenReturn(ENTRY);
        queueWord("lantern", 0);

        runner.runNow("test", null, null);
        assertThat(item("lantern").getStatus()).isEqualTo("failed");

        PrefillRunner.RunSummary second = runner.runNow("test", null, null);

        assertThat(second.done()).isEqualTo(1);
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        assertThat(item("lantern").getLastError()).isNull();
    }

    @Test
    void onlyOneRunAtATime() throws Exception {
        CountDownLatch insideClaude = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(claude.describeWord(anyString())).thenAnswer(call -> {
            insideClaude.countDown();
            release.await(10, TimeUnit.SECONDS);
            return ENTRY;
        });
        queueWord("lantern", 0);

        runner.startInBackground("test", null, null);
        assertThat(insideClaude.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(runner.isRunning()).isTrue();

        assertThatThrownBy(() -> runner.startInBackground("test", null, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("prefill_running");
        assertThatThrownBy(() -> runner.runNow("test", null, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("prefill_running");

        release.countDown();
        for (int i = 0; i < 100 && runner.isRunning(); i++) {
            Thread.sleep(50);
        }
        assertThat(runner.isRunning()).isFalse();
        assertThat(item("lantern").getStatus()).isEqualTo("done");
    }

    @Test
    void aNormalUserStillNeedsToSignInWhilePrefillExists() {
        // The prefill path is only open on a prefill thread: here, on a plain thread, a guest is still refused.
        assertThatThrownBy(() -> wordService.lookUp("pebble")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("sign_in_required");
    }
}
