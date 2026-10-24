package com.englishtranslation.backend.prefill;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.translation.PersonalTextDetector;
import com.englishtranslation.backend.word.EnglishWords;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Remembers the words of translated sentences for the nightly prefill job (TASK-83.8).
 *
 * A learner taps a word inside a sentence result. If that word is not bundled and not cached, the tap costs a Claude
 * call (and needs sign-in). So after a sentence was answered, its words that would cost a call are put in
 * {@code prefill_item} (kind word, source {@code sentence-words}); the next night the {@link PrefillRunner} answers them.
 *
 * <ul>
 * <li>Off the request path: {@link #offer} only hands the text to a small worker thread and returns. When the worker is
 * busy and its queue (50) is full, the text is dropped. Errors are logged, never thrown.</li>
 * <li>Personal text is never looked at. The sentence is never saved, only single words.</li>
 * <li>A word is queued when it is on the English word list, is not bundled (as a lemma or as a form of a bundled word)
 * and is not cached for the current word prompt version. A form like "giraffes" is skipped when its obvious base
 * ("giraffe") is bundled or cached: the learner finds the base there.</li>
 * <li>A word that is queued already gets {@code seen_count + 1} and a higher priority. A row that is done or skipped
 * stays as it is.</li>
 * </ul>
 */
@Service
public class SentenceWordQueue {

    private static final Logger log = LoggerFactory.getLogger(SentenceWordQueue.class);

    public static final String SOURCE = "sentence-words";

    /**
     * Priority of a word seen once, and what each more sighting adds, up to the max. The pipeline gives its words
     * {@code round(zipf * 100)} (about 300-600 for the words learners meet), so these stay on the same scale: a
     * word seen in 10 sentences is answered before common pipeline words, a word seen once after most of them.
     */
    static final int BASE_PRIORITY = 300;
    static final int PRIORITY_PER_SIGHTING = 50;
    static final int MAX_PRIORITY = 800;

    private static final int WAITING_TEXTS = 50;
    private static final int MAX_WORDS_PER_SENTENCE = 60;
    private static final int MIN_WORD_LENGTH = 2;

    /** A word with inner apostrophes (don't, it's); the apostrophe may be the curly one. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)*");

    private final PersonalTextDetector personalText;
    private final EnglishWords englishWords;
    private final DictionaryWordRepository bundled;
    private final NamedParameterJdbcTemplate jdbc;
    private final WordLookupRepository lookups;
    private final PrefillItemRepository queue;
    private final ClaudeProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final ThreadPoolExecutor ownExecutor;

    @Autowired
    public SentenceWordQueue(PersonalTextDetector personalText, EnglishWords englishWords,
            DictionaryWordRepository bundled, NamedParameterJdbcTemplate jdbc, WordLookupRepository lookups,
            PrefillItemRepository queue, ClaudeProperties properties, Clock clock) {
        this(personalText, englishWords, bundled, jdbc, lookups, queue, properties, clock, null);
    }

    /** For tests: {@code executor} null means the real bounded worker thread. */
    SentenceWordQueue(PersonalTextDetector personalText, EnglishWords englishWords, DictionaryWordRepository bundled,
            NamedParameterJdbcTemplate jdbc, WordLookupRepository lookups, PrefillItemRepository queue,
            ClaudeProperties properties, Clock clock, Executor executor) {
        this.personalText = personalText;
        this.englishWords = englishWords;
        this.bundled = bundled;
        this.jdbc = jdbc;
        this.lookups = lookups;
        this.queue = queue;
        this.properties = properties;
        this.clock = clock;
        if (executor == null) {
            // One thread, a queue of 50 texts, and DiscardPolicy: a full queue drops the new text (nobody waits for it).
            this.ownExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(WAITING_TEXTS), task -> {
                        Thread thread = new Thread(task, "sentence-words");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.DiscardPolicy());
            this.executor = ownExecutor;
        } else {
            this.ownExecutor = null;
            this.executor = executor;
        }
    }

    /**
     * Called after a sentence was answered. Returns at once and never throws; the work happens on the worker thread.
     * {@code sentence} is the text the user sent.
     */
    public void offer(String sentence) {
        try {
            if (sentence == null || sentence.isBlank()) {
                return;
            }
            // Checked here too, so personal text does not even reach the worker.
            if (personalText.looksPersonal(CacheKeys.normalizeSentence(sentence))) {
                return;
            }
            executor.execute(() -> {
                try {
                    queueWords(sentence);
                } catch (RuntimeException e) {
                    log.warn("Could not queue the words of a sentence: {}", e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("Sentence-word queue is full, text dropped");
        } catch (RuntimeException e) {
            log.warn("Could not hand a sentence to the word queue: {}", e.toString());
        }
    }

    /** Does the work of {@link #offer} on the calling thread. Returns how many words were queued or counted again. */
    int queueWords(String sentence) {
        String normalized = CacheKeys.normalizeSentence(sentence);
        if (personalText.looksPersonal(normalized)) {
            return 0;
        }
        List<String> words = wordsOf(normalized);
        if (words.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> basesOf = new HashMap<>();
        Set<String> all = new HashSet<>(words);
        for (String word : words) {
            List<String> bases = possibleBases(word);
            basesOf.put(word, bases);
            all.addAll(bases);
        }
        Set<String> bundledNow = bundledAmong(all);
        Set<String> cachedNow = cachedAmong(all);

        int changed = 0;
        for (String word : words) {
            if (bundledNow.contains(word) || cachedNow.contains(word)) {
                continue;
            }
            boolean baseIsAnswered = basesOf.get(word).stream()
                    .anyMatch(base -> bundledNow.contains(base) || cachedNow.contains(base));
            if (baseIsAnswered) {
                continue;
            }
            addOrCount(word);
            changed++;
        }
        return changed;
    }

    /**
     * The distinct English words of a sentence, lower case, in order of appearance. A possessive 's is cut off
     * ("Maria's" gives "maria"); other contractions (don't) and anything not on the word list are left out.
     */
    List<String> wordsOf(String sentence) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(sentence);
        while (matcher.find() && found.size() < MAX_WORDS_PER_SENTENCE) {
            String token = matcher.group().replace('’', '\'').toLowerCase(Locale.ROOT);
            if (token.endsWith("'s")) {
                token = token.substring(0, token.length() - 2);
            }
            if (token.contains("'") || token.length() < MIN_WORD_LENGTH) {
                continue;
            }
            String word = CacheKeys.normalizeWord(token);
            if (englishWords.contains(word)) {
                found.add(word);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * The words this word may be a form of, found by the usual endings (-s, -es, -ies, -ing, -ed). Not exact on
     * purpose: it only decides to skip a word, never to change one.
     */
    public static List<String> candidateBases(String word) {
        Set<String> out = new LinkedHashSet<>();
        int n = word.length();
        if (word.endsWith("ies") && n > 4) {
            out.add(word.substring(0, n - 3) + "y");
        }
        if (word.endsWith("es") && n > 3) {
            out.add(word.substring(0, n - 2));
        }
        if (word.endsWith("s") && !word.endsWith("ss") && n > 3) {
            out.add(word.substring(0, n - 1));
        }
        if (word.endsWith("ing") && n > 5) {
            String stem = word.substring(0, n - 3);
            out.add(stem);
            out.add(stem + "e");
            addUndoubled(out, stem);
        }
        if (word.endsWith("ied") && n > 4) {
            out.add(word.substring(0, n - 3) + "y");
        }
        if (word.endsWith("ed") && n > 4) {
            String stem = word.substring(0, n - 2);
            out.add(stem);
            out.add(stem + "e");
            addUndoubled(out, stem);
        }
        out.remove(word);
        return new ArrayList<>(out);
    }

    private static void addUndoubled(Set<String> out, String stem) {
        int n = stem.length();
        if (n > 2 && stem.charAt(n - 1) == stem.charAt(n - 2)) {
            out.add(stem.substring(0, n - 1));
        }
    }

    private List<String> possibleBases(String word) {
        return candidateBases(word).stream().filter(englishWords::contains).toList();
    }

    /** Lemmas, and forms of bundled words ("giraffes"), among these words. */
    private Set<String> bundledAmong(Set<String> words) {
        Set<String> found = new HashSet<>(bundled.findLemmasIn(words));
        found.addAll(jdbc.queryForList("select distinct form from dictionary_form where form in (:forms)",
                new MapSqlParameterSource("forms", words), String.class));
        return found;
    }

    /** Words that have a word_lookup row for the current word prompt version. */
    private Set<String> cachedAmong(Set<String> words) {
        Map<String, String> wordOfKey = new HashMap<>();
        for (String word : words) {
            wordOfKey.put(CacheKeys.key("word", properties.wordPromptVersion(), word), word);
        }
        Set<String> found = new HashSet<>();
        for (String key : lookups.findCacheKeysIn(wordOfKey.keySet())) {
            found.add(wordOfKey.get(key));
        }
        return found;
    }

    /** A new row (seen once), or one more sighting of a pending or failed row. Done and skipped rows stay as they are. */
    private void addOrCount(String word) {
        var existing = queue.findByKindAndTextAndTargetLang(PrefillItem.WORD, word, "");
        if (existing.isPresent()) {
            countAgain(existing.get());
            return;
        }
        try {
            queue.saveAndFlush(new PrefillItem(PrefillItem.WORD, word, "", SOURCE, priorityFor(1), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // The pipeline or the admin queued the same word a moment ago. Count this sighting on that row.
            queue.findByKindAndTextAndTargetLang(PrefillItem.WORD, word, "").ifPresent(this::countAgain);
        }
    }

    private void countAgain(PrefillItem item) {
        if (!PrefillItem.PENDING.equals(item.getStatus()) && !PrefillItem.FAILED.equals(item.getStatus())) {
            return;
        }
        item.seenAgain(priorityFor(item.getSeenCount() + 1));
        queue.save(item);
    }

    /** 300 for one sighting, +50 for each more, at most 800. */
    static int priorityFor(int seenCount) {
        return Math.min(MAX_PRIORITY, BASE_PRIORITY + PRIORITY_PER_SIGHTING * (seenCount - 1));
    }

    @PreDestroy
    void stop() {
        if (ownExecutor != null) {
            ownExecutor.shutdownNow();
        }
    }
}
