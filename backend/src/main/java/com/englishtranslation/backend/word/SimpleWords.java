package com.englishtranslation.backend.word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Checks that a word entry from Claude uses simple words. This is the Java copy of the rules in
 * pipeline/src/pipeline/validate.py ("defining vocabulary": a definition may only use words from a fixed list).
 *
 * The list is words/ngsl.txt (NGSL with all its forms) plus words/simple-extra.txt (the pipeline's
 * extra allowed words). Keep the rules and the extra file in step with validate.py.
 *
 * What is checked: every sense's simpleDefinition (no hard word at all), and its examples
 * (easy: no hard word, medium: at most 1, harder: at most 2). Usage items are not checked.
 */
@Component
public class SimpleWords {

    private static final String[] FILES = {"words/ngsl.txt", "words/simple-extra.txt"};

    private static final Pattern WORD = Pattern.compile("[a-zA-Z]+(?:'[a-z]+)?");

    /** Everyday words that NGSL misses. Only for examples, like EXAMPLE_EXTRA_WORDS in validate.py. */
    private static final Set<String> EXAMPLE_EXTRA = Set.of("me", "my", "oh");

    /** "can't" is "can" + not. Other "n't" words just drop the ending ("don't" becomes "do"). */
    private static final Map<String, String> IRREGULAR_NEGATIVES = Map.of("can't", "can", "won't", "will", "shan't", "shall");

    private static final List<String> CONTRACTION_ENDINGS = List.of("'s", "'m", "'re", "'ve", "'ll", "'d", "n't");

    /** How many words outside the list an example may have. */
    private static final Map<String, Integer> MAX_OUTSIDE_IN_EXAMPLE = Map.of("easy", 0, "medium", 1, "harder", 2);

    private final Set<String> vocabulary = new HashSet<>();

    public SimpleWords() {
        for (String file : FILES) {
            load(file);
        }
    }

    /** The hard words in one definition, sorted. Empty means the definition is simple. */
    public Set<String> hardWordsInDefinition(String definition) {
        Set<String> hard = new TreeSet<>();
        for (String word : words(definition)) {
            // "a person's name" checks "person": the possessive 's is not a new word.
            String plain = word.endsWith("'s") ? word.substring(0, word.length() - 2) : word;
            if (!vocabulary.contains(plain)) {
                hard.add(plain);
            }
        }
        return hard;
    }

    /** The hard words in one example, sorted, not counting the headword. A contraction counts as its base word ("I'm" is "i"). */
    public Set<String> hardWordsInExample(String text, String headword) {
        Set<String> hard = new TreeSet<>();
        for (String word : words(text)) {
            String plain = plainWord(word);
            // An example must use the headword, even a rare one, so the headword and its forms
            // ("reluctant", "reluctantly") are not hard. We do not know all forms, so any word that starts with it is fine.
            if (plain.startsWith(headword)) {
                continue;
            }
            if (!vocabulary.contains(plain) && !EXAMPLE_EXTRA.contains(plain)) {
                hard.add(plain);
            }
        }
        return hard;
    }

    /**
     * Problems of a whole entry, one readable line each, for example
     * "sense 1: the definition uses hard words [abstain]". Empty means the entry is simple enough.
     */
    public List<String> problems(WordEntry entry, String headword) {
        List<String> problems = new ArrayList<>();
        int number = 0;
        for (WordEntry.Sense sense : entry.senses()) {
            number++;
            Set<String> hard = hardWordsInDefinition(nullToEmpty(sense.simpleDefinition()));
            if (!hard.isEmpty()) {
                problems.add("sense " + number + ": the definition uses hard words " + hard);
            }
            if (sense.examples() == null) {
                continue;
            }
            for (WordEntry.Example example : sense.examples()) {
                Integer allowed = MAX_OUTSIDE_IN_EXAMPLE.get(String.valueOf(example.level()).toLowerCase(Locale.ROOT));
                Set<String> hardInExample = hardWordsInExample(nullToEmpty(example.text()), headword.toLowerCase(Locale.ROOT));
                if (allowed != null && hardInExample.size() > allowed) {
                    problems.add("sense " + number + ": the " + example.level() + " example '" + example.text()
                            + "' uses hard words " + hardInExample);
                }
            }
        }
        return problems;
    }

    public int size() {
        return vocabulary.size();
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text.replace('’', '\''));
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    private static String plainWord(String word) {
        if (IRREGULAR_NEGATIVES.containsKey(word)) {
            return IRREGULAR_NEGATIVES.get(word);
        }
        for (String ending : CONTRACTION_ENDINGS) {
            if (word.endsWith(ending)) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private void load(String file) {
        try (InputStream in = new ClassPathResource(file).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    vocabulary.add(word.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read word list " + file, e);
        }
    }
}
