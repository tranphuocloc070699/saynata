package com.englishtranslation.backend.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The backend's own check of the model's fix (TASK-110), so correct text from a book or the news is not
 * "corrected". A fix is dropped when:
 * <ul>
 * <li>the text has more than {@link #MAX_WORDS} words (a learner types short lines; long text is pasted);</li>
 * <li>it is the same text once case, punctuation and curly quotes are ignored;</li>
 * <li>it changes more words than {@link #allowedChanges}: then it is a rewrite, not a fix.</li>
 * </ul>
 */
final class BetterCheck {

    static final int MAX_WORDS = 40;

    private BetterCheck() {
    }

    /** The explanation with its fix, or without it when the fix does not pass. */
    static Explanation apply(String text, Explanation explanation) {
        if (explanation.better() == null) {
            return explanation;
        }
        return keeps(text, explanation.better()) ? explanation : explanation.withoutBetter();
    }

    static boolean keeps(String text, String better) {
        List<String> before = words(text);
        List<String> after = words(better);
        if (before.isEmpty() || after.isEmpty() || before.size() > MAX_WORDS || before.equals(after)) {
            return false;
        }
        int changes = Math.max(before.size(), after.size()) - commonWords(before, after);
        return changes <= allowedChanges(before.size());
    }

    /** At least 3 words (short lines need a tense and a word fixed), else 40% of the words. */
    static int allowedChanges(int wordCount) {
        return Math.max(3, (int) Math.ceil(wordCount * 0.4));
    }

    /** Lowercase words with straight apostrophes; punctuation around a word is not part of it. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        String plain = text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
        for (String piece : plain.split("\\s+")) {
            String word = piece.replaceAll("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$", "");
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** The length of the longest list of words both have in the same order. */
    private static int commonWords(List<String> a, List<String> b) {
        int[][] lengths = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                lengths[i][j] = a.get(i - 1).equals(b.get(j - 1))
                        ? lengths[i - 1][j - 1] + 1
                        : Math.max(lengths[i - 1][j], lengths[i][j - 1]);
            }
        }
        return lengths[a.size()][b.size()];
    }
}
