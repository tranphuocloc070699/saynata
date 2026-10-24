package com.englishtranslation.backend.sources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * A resource file of {@code text<TAB>zipf} lines, loaded into a map. Zipf is how common a word is (wordfreq scale:
 * 7 = "the", 3 = a word you meet now and then, below 3 = rare). The files are made by
 * {@code uv run pipeline source-lists} (TASK-108, doc-6).
 */
final class ZipfList {

    private final Map<String, Double> scores = new HashMap<>();

    ZipfList(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    scores.put(line.substring(0, tab), Double.parseDouble(line.substring(tab + 1).strip()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    /** For tests: a list made in code. */
    ZipfList(Map<String, Double> values) {
        scores.putAll(values);
    }

    boolean contains(String text) {
        return scores.containsKey(text);
    }

    /** The score, or 0 when the text is not in the list. */
    double zipf(String text) {
        return scores.getOrDefault(text, 0.0);
    }

    java.util.Set<String> keys() {
        return scores.keySet();
    }

    int size() {
        return scores.size();
    }
}
