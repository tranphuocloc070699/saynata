package com.englishtranslation.backend.word;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * At start-up: fills word_lookup.cefr for rows saved before the column existed (migration V6).
 * Once every row has a level this does nothing. A row whose JSON cannot be read is skipped.
 */
@Component
public class WordCefrBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WordCefrBackfill.class);

    private final WordLookupRepository repository;
    private final JsonMapper jsonMapper;

    public WordCefrBackfill(WordLookupRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<WordLookup> missing = repository.findByCefrIsNull();
        int filled = 0;
        for (WordLookup lookup : missing) {
            try {
                WordEntry entry = jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
                if (entry.firstCefr() != null) {
                    lookup.setCefr(entry.firstCefr());
                    repository.save(lookup);
                    filled++;
                }
            } catch (RuntimeException e) {
                log.warn("word_lookup {} has entry_json that cannot be read, no level set: {}", lookup.getId(), e.toString());
            }
        }
        if (filled > 0) {
            log.info("Set the CEFR level on {} saved words.", filled);
        }
    }
}
