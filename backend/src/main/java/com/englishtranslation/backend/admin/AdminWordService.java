package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.media.SenseMedia;
import com.englishtranslation.backend.media.SenseMediaReader;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin's words: bundled words (dictionary_word) and backend words (word_lookup) in one list.
 * The list reads the database view admin_word_list, so filters, sorting and paging happen in SQL.
 */
@Service
public class AdminWordService {

    private static final Logger log = LoggerFactory.getLogger(AdminWordService.class);

    static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");

    /** What Refine may send in _sort, and the SQL it means. "id" sorts by source, then by the number. */
    private static final Map<String, List<String>> SORT_COLUMNS = Map.of(
            "id", List.of("source", "source_id"),
            "word", List.of("word"),
            "source", List.of("source"),
            "needsReview", List.of("needs_review"),
            "model", List.of("model"),
            "promptVersion", List.of("prompt_version"),
            "createdAt", List.of("created_at"));

    /** One page of words and how many words match in all (for X-Total-Count). */
    public record WordPage(List<AdminWord> words, long total) {
    }

    /** One row of the view, before the entry is added. */
    private record ListRow(String itemId, String source, String word, String ipa, boolean needsReview, String model,
            String promptVersion, Instant createdAt) {
    }

    private final JdbcClient jdbc;
    private final DictionaryWordRepository dictionary;
    private final WordLookupRepository lookups;
    private final JsonMapper jsonMapper;
    private final EntryParser parser;
    private final SenseMediaReader mediaReader;

    public AdminWordService(JdbcClient jdbc, DictionaryWordRepository dictionary, WordLookupRepository lookups,
            JsonMapper jsonMapper, EntryParser parser, SenseMediaReader mediaReader) {
        this.mediaReader = mediaReader;
        this.jdbc = jdbc;
        this.dictionary = dictionary;
        this.lookups = lookups;
        this.jsonMapper = jsonMapper;
        this.parser = parser;
    }

    // ---- list ----

    /**
     * @param likePattern   from AdminListParams.likePattern
     * @param needsReview   null = both
     * @param source        null = both, else "bundled" or "backend"
     * @param cefr          empty = every level, else levels like A1, B2 (the level of the first sense)
     * @param pageable      from AdminListParams.pageable; its sort names are the keys of sortColumns()
     */
    public WordPage list(String likePattern, Boolean needsReview, String source, List<String> cefr, Pageable pageable) {
        List<String> levels = checkLevels(cefr);
        if (source != null && !source.equals(AdminWordId.BUNDLED) && !source.equals(AdminWordId.BACKEND)) {
            throw new InvalidAdminRequestException("source must be one of: backend, bundled.");
        }

        StringBuilder where = new StringBuilder(" where lower(word) like :like escape '\\'");
        Map<String, Object> params = new HashMap<>();
        params.put("like", likePattern);
        if (needsReview != null) {
            where.append(" and needs_review = :needsReview");
            params.put("needsReview", needsReview);
        }
        if (source != null) {
            where.append(" and source = :source");
            params.put("source", source);
        }
        if (!levels.isEmpty()) {
            where.append(" and cefr in (:cefr)");
            params.put("cefr", levels);
        }

        long total = jdbc.sql("select count(*) from admin_word_list" + where)
                .params(params).query(Long.class).single();

        String sql = "select item_id, source, word, ipa, needs_review, model, prompt_version, created_at"
                + " from admin_word_list" + where + " order by " + orderBy(pageable.getSort())
                + " limit :limit offset :offset";
        params.put("limit", pageable.getPageSize());
        params.put("offset", pageable.getOffset());
        List<ListRow> rows = jdbc.sql(sql).params(params).query((rs, n) -> new ListRow(
                rs.getString("item_id"), rs.getString("source"), rs.getString("word"), rs.getString("ipa"),
                rs.getBoolean("needs_review"), rs.getString("model"), rs.getString("prompt_version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant())).list();

        return new WordPage(withEntries(rows), total);
    }

    /** The names the admin may use in _sort. */
    static Set<String> sortNames() {
        return SORT_COLUMNS.keySet();
    }

    /** Refine sends one sort. The id is added last so equal values always come in the same order. */
    private String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String direction = order.isAscending() ? " asc" : " desc";
            for (String column : SORT_COLUMNS.get(order.getProperty())) {
                parts.add(column + direction);
            }
        }
        parts.add("source asc");
        parts.add("source_id asc");
        return String.join(", ", parts);
    }

    private List<String> checkLevels(List<String> cefr) {
        List<String> levels = new ArrayList<>();
        for (String level : cefr == null ? List.<String>of() : cefr) {
            String upper = level.trim().toUpperCase();
            if (!LEVELS.contains(upper)) {
                throw new InvalidAdminRequestException("cefr must be a list of: A1, A2, B1, B2, C1, C2.");
            }
            levels.add(upper);
        }
        return levels;
    }

    /** Adds the entry to every row: one query for the bundled words, one for the backend words. */
    private List<AdminWord> withEntries(List<ListRow> rows) {
        List<Long> bundledIds = new ArrayList<>();
        List<Long> backendIds = new ArrayList<>();
        for (ListRow row : rows) {
            AdminWordId id = AdminWordId.parse(row.itemId());
            (id.bundled() ? bundledIds : backendIds).add(id.number());
        }
        Map<Long, WordEntry> bundledEntries = new HashMap<>();
        if (!bundledIds.isEmpty()) {
            for (DictionaryWord word : dictionary.findAllByIdIn(bundledIds)) {
                bundledEntries.put(word.getId(), readBundledEntry(word));
            }
        }
        Map<Long, WordEntry> backendEntries = new HashMap<>();
        if (!backendIds.isEmpty()) {
            for (WordLookup lookup : lookups.findAllById(backendIds)) {
                backendEntries.put(lookup.getId(), readBackendEntry(lookup));
            }
        }
        Map<Long, Map<Integer, SenseMedia>> bundledMedia = mediaReader.forBundledWords(bundledIds);
        Map<Long, Map<Integer, SenseMedia>> backendMedia = mediaReader.forBackendWords(backendIds);
        List<AdminWord> words = new ArrayList<>();
        for (ListRow row : rows) {
            AdminWordId id = AdminWordId.parse(row.itemId());
            WordEntry entry = (id.bundled() ? bundledEntries : backendEntries).get(id.number());
            Map<Integer, SenseMedia> media = (id.bundled() ? bundledMedia : backendMedia).getOrDefault(id.number(), Map.of());
            words.add(new AdminWord(row.itemId(), row.word(), entry, row.ipa(), row.needsReview(), row.source(),
                    row.model(), row.promptVersion(), row.createdAt(), mediaPerSense(entry, media)));
        }
        return words;
    }

    // ---- one word ----

    @Transactional(readOnly = true)
    public AdminWord get(String id) {
        AdminWordId wordId = AdminWordId.parse(id);
        return wordId.bundled() ? toAdminWord(findBundled(wordId)) : toAdminWord(findBackend(wordId));
    }

    /**
     * Saves a fixed entry. The body is checked like before (EntryParser). For a bundled word the senses are matched
     * by position: sense 1 stays sense 1 and keeps its picture. Senses added at the end have no picture,
     * and senses left out at the end are removed.
     */
    @Transactional
    public AdminWord update(String id, JsonNode body) {
        AdminWordId wordId = AdminWordId.parse(id);
        if (wordId.bundled()) {
            DictionaryWord word = findBundled(wordId);
            WordEntry entry = parser.parseWordEntry(body);
            applyEntry(word, entry);
            return toAdminWord(dictionary.save(word));
        }
        WordLookup lookup = findBackend(wordId);
        WordEntry entry = parser.parseWordEntry(body);
        lookup.setEntryJson(jsonMapper.writeValueAsString(entry));
        lookup.setCefr(entry.firstCefr());
        return toAdminWord(lookups.save(lookup));
    }

    /** The person looked at the word and it is fine: needsReview becomes false. */
    @Transactional
    public AdminWord markReviewed(String id) {
        AdminWordId wordId = AdminWordId.parse(id);
        if (wordId.bundled()) {
            DictionaryWord word = findBundled(wordId);
            word.setNeedsReview(false);
            return toAdminWord(dictionary.save(word));
        }
        WordLookup lookup = findBackend(wordId);
        lookup.setNeedsReview(false);
        return toAdminWord(lookups.save(lookup));
    }

    /**
     * Deletes a backend word; the next public lookup asks Claude again ("regenerate").
     * A bundled word is not deleted here: it would disappear from the next dictionary.sqlite.
     */
    @Transactional
    public AdminWord delete(String id) {
        AdminWordId wordId = AdminWordId.parse(id);
        if (wordId.bundled()) {
            findBundled(wordId);
            throw new InvalidAdminRequestException("A bundled word cannot be deleted here. Edit it instead.");
        }
        WordLookup lookup = findBackend(wordId);
        lookups.delete(lookup);
        return toAdminWord(lookup);
    }

    private DictionaryWord findBundled(AdminWordId id) {
        return dictionary.findWithSensesById(id.number()).orElseThrow(() -> notFound(id));
    }

    private WordLookup findBackend(AdminWordId id) {
        return lookups.findById(id.number()).orElseThrow(() -> notFound(id));
    }

    private ResponseStatusException notFound(AdminWordId id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No word with id " + id.text() + ".");
    }

    // ---- bundled word <-> WordEntry ----

    private void applyEntry(DictionaryWord word, WordEntry entry) {
        List<DictionarySense> senses = word.getSenses();
        for (int i = 0; i < entry.senses().size(); i++) {
            WordEntry.Sense sent = entry.senses().get(i);
            DictionarySense sense;
            if (i < senses.size()) {
                sense = senses.get(i);
            } else {
                sense = new DictionarySense(word, i);
                senses.add(sense);
            }
            sense.setText(sent.pos(), sent.cefr(), sent.simpleDefinition(), sent.vietnamese(),
                    jsonMapper.writeValueAsString(sent.examples()), jsonMapper.writeValueAsString(sent.usage()));
        }
        while (senses.size() > entry.senses().size()) {
            senses.remove(senses.size() - 1);
        }
        word.setUpdatedAt(Instant.now());
    }

    private WordEntry readBundledEntry(DictionaryWord word) {
        try {
            List<WordEntry.Sense> senses = new ArrayList<>();
            for (DictionarySense sense : word.getSenses()) {
                senses.add(new WordEntry.Sense(sense.getPos(), sense.getCefr(), sense.getSimpleDefinition(),
                        sense.getVietnamese(),
                        jsonMapper.readValue(sense.getExamplesJson(), new TypeReference<List<WordEntry.Example>>() { }),
                        jsonMapper.readValue(sense.getUsageJson(), new TypeReference<List<WordEntry.Usage>>() { })));
            }
            return new WordEntry(senses);
        } catch (RuntimeException e) {
            log.warn("dictionary_word {} has examples or usage JSON that cannot be read: {}", word.getId(), e.toString());
            return null;
        }
    }

    private WordEntry readBackendEntry(WordLookup lookup) {
        try {
            return jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
        } catch (RuntimeException e) {
            // A broken old row must not break the whole list. The admin sees entry = null and can delete it.
            log.warn("word_lookup {} has entry_json that cannot be read: {}", lookup.getId(), e.toString());
            return null;
        }
    }

    private AdminWord toAdminWord(DictionaryWord word) {
        WordEntry entry = readBundledEntry(word);
        List<SenseMedia> media = mediaPerSense(entry, mediaReader.forBundledWords(List.of(word.getId()))
                .getOrDefault(word.getId(), Map.of()));
        return new AdminWord("b-" + word.getId(), word.getLemma(), entry, word.getIpa(),
                word.isNeedsReview(), AdminWordId.BUNDLED, word.getModel(), word.getPromptVersion(),
                word.getCreatedAt(), media);
    }

    private AdminWord toAdminWord(WordLookup lookup) {
        WordEntry entry = readBackendEntry(lookup);
        List<SenseMedia> media = mediaPerSense(entry, mediaReader.forBackendWords(List.of(lookup.getId()))
                .getOrDefault(lookup.getId(), Map.of()));
        return new AdminWord("w-" + lookup.getId(), lookup.getWord(), entry, null,
                lookup.isNeedsReview(), AdminWordId.BACKEND, lookup.getModel(), lookup.getPromptVersion(),
                lookup.getCreatedAt(), media);
    }

    /** One item per sense, in the order of the entry: the media, or null when the sense has none. */
    private List<SenseMedia> mediaPerSense(WordEntry entry, Map<Integer, SenseMedia> byPosition) {
        List<SenseMedia> media = new ArrayList<>();
        if (entry != null) {
            for (int i = 0; i < entry.senses().size(); i++) {
                media.add(byPosition.get(i));
            }
        }
        return media;
    }
}
