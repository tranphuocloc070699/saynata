package com.englishtranslation.backend.word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The set of real English words, loaded once at startup. An LLM cannot decide what a word is
 * (it "corrects" the spelling instead), so WordService asks this list before it calls Claude.
 *
 * Files in resources/words: scowl-70.txt (SCOWL, see SCOWL-LICENSE.txt) and ngsl.txt (the words
 * of the bundled dictionary, with their forms). One lowercase word per line.
 */
@Component
public class EnglishWords {

    private static final String[] FILES = {"words/scowl-70.txt", "words/ngsl.txt"};

    private final Set<String> words = new HashSet<>();

    public EnglishWords() {
        for (String file : FILES) {
            load(file);
        }
    }

    /** True if the word is on the list. Pass the normalised word; case does not matter. */
    public boolean contains(String word) {
        return words.contains(word.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return words.size();
    }

    private void load(String file) {
        try (InputStream in = new ClassPathResource(file).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read word list " + file, e);
        }
    }
}
