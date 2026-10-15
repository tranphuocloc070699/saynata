package com.englishtranslation.backend.dictionary;

import com.englishtranslation.backend.word.WordEntry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a bundled word (dictionary_word with its senses) into the same WordEntry that Claude writes for a
 * backend word. Used by the admin and by GET /api/words/{word}.
 */
@Component
public class BundledEntryReader {

    private final JsonMapper jsonMapper;

    public BundledEntryReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Needs the senses to be loaded. Throws a RuntimeException when the examples or usage JSON cannot be read. */
    public WordEntry read(DictionaryWord word) {
        List<WordEntry.Sense> senses = new ArrayList<>();
        for (DictionarySense sense : word.getSenses()) {
            senses.add(new WordEntry.Sense(sense.getPos(), sense.getCefr(), sense.getSimpleDefinition(),
                    sense.getVietnamese(),
                    jsonMapper.readValue(sense.getExamplesJson(), new TypeReference<List<WordEntry.Example>>() { }),
                    jsonMapper.readValue(sense.getUsageJson(), new TypeReference<List<WordEntry.Usage>>() { })));
        }
        return new WordEntry(senses);
    }
}
