package com.englishtranslation.backend.prefill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.PersonalTextDetector;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.translation.TargetLanguage;
import com.englishtranslation.backend.word.EnglishWords;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookupRepository;
import com.englishtranslation.backend.word.WordService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The words of translated sentences are queued for the nightly prefill job (TASK-83.8), with a fake Claude.
 * The test database has no bundled words, so each test bundles the words it needs.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.prefill.delay=0s"
})
@AutoConfigureMockMvc
class SentenceWordQueueTest {

    static final WordEntry ENTRY = new WordEntry(List.of(new WordEntry.Sense(
            "noun", "B1", "a thing", "một thứ", List.of(), List.of())));
    static final Explanation EXPLANATION = new Explanation("A light.", List.of(), "Một ánh sáng");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SentenceWordQueue sentenceWords;
    @Autowired
    PrefillRunner runner;
    @Autowired
    PrefillItemRepository queue;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    NamedParameterJdbcTemplate jdbc;
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
    @Autowired
    EnglishWords englishWords;
    @Autowired
    PersonalTextDetector personalText;
    @Autowired
    Clock clock;
    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void clean() {
        queue.deleteAll();
        words.deleteAll();
        sentences.deleteAll();
        usageRows.deleteAll();
        jdbc.getJdbcTemplate().update("delete from dictionary_form");
        bundled.deleteAll();
    }

    private void bundle(String lemma, String... forms) {
        DictionaryWord word = bundled.saveAndFlush(new DictionaryWord(lemma, 1, null));
        for (String form : forms) {
            jdbc.getJdbcTemplate().update("insert into dictionary_form (word_id, form, form_type) values (?, ?, 'plural')",
                    word.getId(), form);
        }
    }

    private List<String> queuedTexts() {
        return queue.findAll().stream().map(PrefillItem::getText).toList();
    }

    private PrefillItem item(String text) {
        return queue.findByKindAndTextAndTargetLang("word", text, "").orElseThrow();
    }

    /** The queue is filled by another thread after the response, so wait for it (at most 5 seconds). */
    private void waitUntilQueued(String text) throws InterruptedException {
        for (int i = 0; i < 100 && queue.findByKindAndTextAndTargetLang("word", text, "").isEmpty(); i++) {
            Thread.sleep(50);
        }
    }

    @Test
    void wordsOfATranslatedSentenceAreQueuedAfterTheResponse() throws Exception {
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);

        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"The lantern glows.\", \"targetLang\": \"vi\"}"))
                .andExpect(status().isOk());
        waitUntilQueued("lantern");
        waitUntilQueued("glows"); // the words are saved one by one, so wait for the last one too

        PrefillItem lantern = item("lantern");
        assertThat(lantern.getSource()).isEqualTo("sentence-words");
        assertThat(lantern.getStatus()).isEqualTo("pending");
        assertThat(lantern.getTargetLang()).isEmpty();
        assertThat(lantern.getSeenCount()).isEqualTo(1);
        assertThat(lantern.getPriority()).isEqualTo(300);
        assertThat(queuedTexts()).contains("glows");
        // Only single words are stored, never the sentence.
        assertThat(queue.findAll()).allMatch(row -> row.getKind().equals("word") && !row.getText().contains(" "));
    }

    @Test
    void aCachedAnswerQueuesItsWordsToo() throws Exception {
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);
        String body = "{\"text\": \"A lantern.\", \"targetLang\": \"vi\"}";

        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        waitUntilQueued("lantern");
        queue.deleteAll();
        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()); // answered from the sentence cache
        waitUntilQueued("lantern");

        assertThat(queuedTexts()).contains("lantern");
    }

    @Test
    void aCachedOnlyPrefetchQueuesNothing() throws Exception {
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);
        String body = "{\"text\": \"A lantern.\", \"targetLang\": \"vi\"}";

        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        waitUntilQueued("lantern");
        queue.deleteAll();
        // The app's prefetch while the user types: a cache hit, but the half-typed text must not raise the counts.
        mockMvc.perform(post("/api/translations?cachedOnly=true").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        Thread.sleep(300); // the worker would have had time to queue something

        assertThat(queue.findAll()).isEmpty();
    }

    @Test
    void theStreamingEndpointQueuesTheWordsToo() throws Exception {
        when(claude.explainSentenceStreaming(anyString(), any())).thenReturn(EXPLANATION);

        var started = mockMvc.perform(post("/api/translations/stream").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"The lantern glows.\", \"targetLang\": \"vi\"}")).andReturn();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(started));
        waitUntilQueued("lantern");
        waitUntilQueued("glows"); // the worker saves the words one by one

        assertThat(queuedTexts()).contains("lantern", "glows");
    }

    @Test
    void personalTextIsNeverScanned() throws Exception {
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);

        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Write to jane@example.com about the lantern\", \"targetLang\": \"vi\"}"))
                .andExpect(status().isOk());
        Thread.sleep(300); // the worker would have had time to queue something

        assertThat(queue.findAll()).isEmpty();
        assertThat(sentenceWords.queueWords("Call 0912345678 about the lantern")).isZero();
        assertThat(queue.findAll()).isEmpty();
    }

    @Test
    void bundledWordsAndTheirFormsAreNotQueued() {
        bundle("lantern");
        bundle("giraffe", "giraffes");

        sentenceWords.queueWords("The lantern and the giraffes are hesitant.");

        assertThat(queuedTexts()).contains("hesitant").doesNotContain("lantern", "giraffes", "giraffe");
    }

    @Test
    void cachedWordsAreNotQueued() {
        when(claude.describeWord("hesitant")).thenReturn(ENTRY);
        wordService.lookUp("hesitant");

        sentenceWords.queueWords("A hesitant lantern.");

        assertThat(queuedTexts()).contains("lantern").doesNotContain("hesitant");
    }

    @Test
    void notWordsAreNotQueued() {
        sentenceWords.queueWords("Qwzxv 1234 lantern, don't! x");

        assertThat(queuedTexts()).contains("lantern").doesNotContain("qwzxv", "1234", "don't", "don", "x");
    }

    @Test
    void aPossessiveIsCutAndPunctuationIsStripped() {
        sentenceWords.queueWords("Maria’s \"lantern\" (glows)!");

        assertThat(queuedTexts()).contains("maria", "lantern", "glows");
        assertThat(queuedTexts()).noneMatch(text -> text.contains("'") || text.contains("\"") || text.contains("("));
    }

    @Test
    void aFormWhoseBaseIsBundledOrCachedIsSkipped() {
        bundle("giraffe"); // no form row: the ending rule finds the base
        when(claude.describeWord("lantern")).thenReturn(ENTRY);
        wordService.lookUp("lantern");

        sentenceWords.queueWords("giraffes lanterns");

        assertThat(queuedTexts()).doesNotContain("giraffes", "lanterns");
    }

    @Test
    void aFormWhoseBaseIsUnknownIsQueuedAsWritten() {
        sentenceWords.queueWords("lanterns");

        assertThat(queuedTexts()).containsExactly("lanterns");
    }

    @Test
    void seenAgainRaisesTheCounterAndThePriorityWithoutADuplicate() {
        sentenceWords.queueWords("lantern");
        sentenceWords.queueWords("A lantern");
        sentenceWords.queueWords("lantern lantern lantern"); // one sighting per sentence

        assertThat(queue.findAll().stream().filter(row -> row.getText().equals("lantern"))).hasSize(1);
        assertThat(item("lantern").getSeenCount()).isEqualTo(3);
        assertThat(item("lantern").getPriority()).isEqualTo(400);
    }

    @Test
    void thePriorityStopsAt800AndNeverLowersAPipelinePriority() {
        queue.save(new PrefillItem("word", "lantern", "", "words", 650, Instant.now()));
        for (int i = 0; i < 20; i++) {
            sentenceWords.queueWords("lantern hesitant");
        }

        assertThat(item("lantern").getSource()).isEqualTo("words");
        assertThat(item("lantern").getSeenCount()).isEqualTo(21);
        assertThat(item("lantern").getPriority()).isEqualTo(800);
        assertThat(item("hesitant").getPriority()).isEqualTo(800);

        queue.save(new PrefillItem("word", "glow", "", "words", 950, Instant.now()));
        sentenceWords.queueWords("glow");
        assertThat(item("glow").getPriority()).isEqualTo(950);
    }

    @Test
    void aDoneOrSkippedRowStaysAsItIs() {
        PrefillItem done = new PrefillItem("word", "lantern", "", "words", 100, Instant.now());
        done.finish("done", null, Instant.now());
        queue.save(done);
        PrefillItem skipped = new PrefillItem("word", "hesitant", "", "words", 100, Instant.now());
        skipped.finish("skipped", "not an English word", Instant.now());
        queue.save(skipped);

        sentenceWords.queueWords("lantern hesitant");

        assertThat(item("lantern").getSeenCount()).isEqualTo(1);
        assertThat(item("lantern").getPriority()).isEqualTo(100);
        assertThat(item("lantern").getStatus()).isEqualTo("done");
        assertThat(item("hesitant").getSeenCount()).isEqualTo(1);
        assertThat(item("hesitant").getStatus()).isEqualTo("skipped");
    }

    @Test
    void offerReturnsBeforeTheWorkIsDone() {
        List<Runnable> waiting = new ArrayList<>();
        SentenceWordQueue slow = new SentenceWordQueue(personalText, englishWords, bundled, jdbc, words, queue,
                properties, clock, waiting::add);

        slow.offer("The lantern glows.");

        assertThat(queue.findAll()).isEmpty(); // nothing happened on the calling thread
        assertThat(waiting).hasSize(1);
        waiting.get(0).run();
        assertThat(queuedTexts()).contains("lantern");
    }

    @Test
    void aFullQueueOrAFailingWorkerNeverReachesTheCaller() {
        SentenceWordQueue full = new SentenceWordQueue(personalText, englishWords, bundled, jdbc, words, queue,
                properties, clock, task -> {
                    throw new RejectedExecutionException("full");
                });
        WordLookupRepository broken = mock(WordLookupRepository.class);
        when(broken.findCacheKeysIn(any())).thenThrow(new IllegalStateException("database down"));
        SentenceWordQueue failing = new SentenceWordQueue(personalText, englishWords, bundled, jdbc, broken, queue,
                properties, clock, Runnable::run);

        assertThatCode(() -> full.offer("The lantern glows.")).doesNotThrowAnyException();
        assertThatCode(() -> failing.offer("The lantern glows.")).doesNotThrowAnyException();
        assertThat(queue.findAll()).isEmpty();
    }

    @Test
    void theNightlyJobAnswersQueuedSentenceWordsThroughThePrefillCap() {
        // Each Claude call costs $0.10 (1,000,000 input tokens of the Haiku model); the run cap is $0.15.
        when(claude.describeWord(anyString())).thenAnswer(call -> {
            usage.record(Feature.WORD, properties.model(), 1_000_000, 0);
            return ENTRY;
        });
        sentenceWords.queueWords("lantern lantern");
        sentenceWords.queueWords("lantern");
        sentenceWords.queueWords("hesitant");
        queue.findAll().stream().filter(row -> !List.of("lantern", "hesitant").contains(row.getText()))
                .forEach(queue::delete);

        PrefillRunner.RunSummary summary = runner.runNow("test", new BigDecimal("0.15"), null);

        assertThat(summary.stoppedBecause()).isEqualTo("cap");
        assertThat(summary.done()).isEqualTo(1);
        assertThat(item("lantern").getStatus()).isEqualTo("done"); // seen twice, so answered first
        assertThat(item("hesitant").getStatus()).isEqualTo("pending");
        assertThat(usageRows.findAll()).hasSize(1).allMatch(row -> row.getFeature() == Feature.PREFILL);
        assertThat(words.findAll()).hasSize(1);
    }
}
