package com.englishtranslation.backend.sources;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The real {@link KnownLookup}: a word is known when it is a bundled lemma, a form of a bundled word, cached in
 * word_lookup for the current word prompt version, or already in prefill_item. A phrase is known when it is cached
 * as a word card or as a Vietnamese sentence, or already in prefill_item. Lists are asked in chunks, so a whole
 * book is a few hundred small queries and no more.
 */
@Component
public class DatabaseKnownLookup implements KnownLookup {

    static final int CHUNK = 1000;
    private static final String PHRASE_LANG = "vi";

    private final DictionaryWordRepository bundled;
    private final WordLookupRepository lookups;
    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeProperties properties;

    public DatabaseKnownLookup(DictionaryWordRepository bundled, WordLookupRepository lookups,
            NamedParameterJdbcTemplate jdbc, ClaudeProperties properties) {
        this.bundled = bundled;
        this.lookups = lookups;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public Set<String> knownWords(Collection<String> words) {
        Set<String> known = new HashSet<>();
        for (List<String> chunk : chunks(words)) {
            known.addAll(bundled.findLemmasIn(chunk));
            known.addAll(jdbc.queryForList("select distinct form from dictionary_form where form in (:values)",
                    new MapSqlParameterSource("values", chunk), String.class));
            known.addAll(queuedTexts(chunk, "word"));
            Map<String, String> wordOfKey = new HashMap<>();
            for (String word : chunk) {
                wordOfKey.put(CacheKeys.key("word", properties.wordPromptVersion(), word), word);
            }
            for (String key : lookups.findCacheKeysIn(wordOfKey.keySet())) {
                known.add(wordOfKey.get(key));
            }
        }
        return known;
    }

    @Override
    public Set<String> knownPhrases(Collection<String> phrases) {
        Set<String> known = new HashSet<>();
        for (List<String> chunk : chunks(phrases)) {
            known.addAll(queuedTexts(chunk, "sentence"));
            Map<String, String> wordKeys = new HashMap<>();
            Map<String, String> sentenceKeys = new HashMap<>();
            for (String phrase : chunk) {
                wordKeys.put(CacheKeys.key("word", properties.wordPromptVersion(), phrase), phrase);
                sentenceKeys.put(CacheKeys.sentenceMatchKey(properties.sentencePromptVersion(), PHRASE_LANG, phrase),
                        phrase);
            }
            for (String key : lookups.findCacheKeysIn(wordKeys.keySet())) {
                known.add(wordKeys.get(key));
            }
            for (String key : jdbc.queryForList("select match_key from sentence_translation where match_key in (:values)",
                    new MapSqlParameterSource("values", sentenceKeys.keySet()), String.class)) {
                known.add(sentenceKeys.get(key));
            }
        }
        return known;
    }

    /** The texts (lower case) among these that are rows of the queue with this kind. */
    private Set<String> queuedTexts(List<String> texts, String kind) {
        return new HashSet<>(jdbc.queryForList(
                "select distinct lower(text) from prefill_item where kind = :kind and lower(text) in (:values)",
                new MapSqlParameterSource("values", texts).addValue("kind", kind), String.class));
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<String> all = new ArrayList<>(values);
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) {
            out.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
        }
        return out;
    }
}
