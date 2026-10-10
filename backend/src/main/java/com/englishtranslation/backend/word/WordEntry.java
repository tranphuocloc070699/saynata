package com.englishtranslation.backend.word;

import java.util.List;

/** Same shape as pipeline/src/pipeline/models.py, in camelCase and without the image fields. */
public record WordEntry(List<Sense> senses) {

    public record Sense(
            String pos,
            String cefr,
            String simpleDefinition,
            String vietnamese,
            List<String> examples,
            List<String> collocations,
            List<String> synonyms) {
    }
}
