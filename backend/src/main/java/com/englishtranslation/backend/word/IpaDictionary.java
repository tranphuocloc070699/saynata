package com.englishtranslation.backend.word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * IPA (how to say a word), loaded once at startup from words/ipa-dict-en_US.txt.
 * Source: open-dict-data/ipa-dict, MIT license, see IPA-DICT-LICENSE.txt.
 *
 * Each line is: word, a tab, then one or more IPA strings, for example "hello\t/həˈɫoʊ/, /hɛˈɫoʊ/".
 * We keep only the first IPA. The list is free, so we do not need an LLM call for IPA.
 */
@Component
public class IpaDictionary {

    private static final String FILE = "words/ipa-dict-en_US.txt";

    private final Map<String, String> ipaByWord = new HashMap<>();

    public IpaDictionary() {
        try (InputStream in = new ClassPathResource(FILE).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String word = line.substring(0, tab).strip().toLowerCase(Locale.ROOT);
                String firstIpa = line.substring(tab + 1).split(",")[0].strip();
                if (!word.isEmpty() && !firstIpa.isEmpty()) {
                    ipaByWord.putIfAbsent(word, firstIpa);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read IPA list " + FILE, e);
        }
    }

    /** The IPA with slashes, for example "/ˈɡɹeɪvˌjɑɹd/", or null when the word is not on the list. */
    public String ipaFor(String word) {
        return ipaByWord.get(word.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return ipaByWord.size();
    }
}
