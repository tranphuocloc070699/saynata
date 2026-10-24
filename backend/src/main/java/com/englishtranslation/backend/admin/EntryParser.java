package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.word.WordEntry;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks that JSON sent by the admin web app really is a word entry or a sentence explanation,
 * the same models the public API returns. A wrong shape gives a 400 with a message that names the problem,
 * so a typo can never be saved and break the app later.
 */
@Component
public class EntryParser {

    private static final Set<String> LEVELS = Set.of("easy", "medium", "harder");
    private static final Set<String> USAGE_KINDS = Set.of("pattern", "collocation", "phrase", "idiom");

    private final JsonMapper jsonMapper;

    public EntryParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Reads body.entry as a WordEntry. */
    public WordEntry parseWordEntry(JsonNode body) {
        WordEntry entry = read(body, "entry", WordEntry.class);
        if (entry.senses() == null || entry.senses().isEmpty()) {
            throw invalid("entry.senses must have at least one sense.");
        }
        for (int i = 0; i < entry.senses().size(); i++) {
            checkSense(entry.senses().get(i), "entry.senses[" + i + "]");
        }
        return entry;
    }

    /** Reads body.explanation as an Explanation (meaning, words, translation). */
    public Explanation parseExplanation(JsonNode body) {
        Explanation explanation = read(body, "explanation", Explanation.class);
        requireText(explanation.meaning(), "explanation.meaning");
        // Since prompt v10 (TASK-106.6) a saved sentence has no translation: the language line is in line_translation.
        if (explanation.words() == null) {
            throw invalid("explanation.words must be a list (it can be empty).");
        }
        for (int i = 0; i < explanation.words().size(); i++) {
            Explanation.Word word = explanation.words().get(i);
            String where = "explanation.words[" + i + "]";
            if (word == null) {
                throw invalid(where + " must be an object.");
            }
            requireText(word.word(), where + ".word");
            requireText(word.partOfSpeech(), where + ".partOfSpeech");
            requireText(word.meaning(), where + ".meaning");
        }
        return explanation;
    }

    private <T> T read(JsonNode body, String field, Class<T> type) {
        JsonNode node = body == null ? null : body.get(field);
        if (node == null || node.isNull()) {
            throw invalid("The body must be a JSON object with a \"" + field + "\" field.");
        }
        try {
            // Unknown fields are an error here: a typo like "simpleDefinitions" would otherwise be dropped silently.
            return jsonMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(node);
        } catch (JacksonException e) {
            throw invalid("\"" + field + "\" is not a valid " + type.getSimpleName() + ": " + e.getOriginalMessage());
        }
    }

    private void checkSense(WordEntry.Sense sense, String where) {
        if (sense == null) {
            throw invalid(where + " must be an object.");
        }
        requireText(sense.pos(), where + ".pos");
        requireText(sense.cefr(), where + ".cefr");
        requireText(sense.simpleDefinition(), where + ".simpleDefinition");
        requireText(sense.vietnamese(), where + ".vietnamese");
        if (sense.examples() == null || sense.examples().isEmpty()) {
            throw invalid(where + ".examples must have at least one example.");
        }
        for (int i = 0; i < sense.examples().size(); i++) {
            WordEntry.Example example = sense.examples().get(i);
            String exampleWhere = where + ".examples[" + i + "]";
            if (example == null) {
                throw invalid(exampleWhere + " must be an object.");
            }
            requireOneOf(example.level(), LEVELS, exampleWhere + ".level");
            requireText(example.text(), exampleWhere + ".text");
        }
        List<WordEntry.Usage> usage = sense.usage();
        if (usage == null) {
            throw invalid(where + ".usage must be a list (it can be empty).");
        }
        for (int i = 0; i < usage.size(); i++) {
            WordEntry.Usage line = usage.get(i);
            String usageWhere = where + ".usage[" + i + "]";
            if (line == null) {
                throw invalid(usageWhere + " must be an object.");
            }
            requireOneOf(line.kind(), USAGE_KINDS, usageWhere + ".kind");
            requireText(line.pattern(), usageWhere + ".pattern");
            requireText(line.example(), usageWhere + ".example");
        }
    }

    private void requireText(String value, String where) {
        if (value == null || value.isBlank()) {
            throw invalid(where + " must not be empty.");
        }
    }

    private void requireOneOf(String value, Set<String> allowed, String where) {
        if (value == null || !allowed.contains(value)) {
            throw invalid(where + " must be one of: " + String.join(", ", allowed.stream().sorted().toList()) + ".");
        }
    }

    private InvalidAdminRequestException invalid(String message) {
        return new InvalidAdminRequestException(message);
    }
}
