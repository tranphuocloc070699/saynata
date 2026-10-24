package com.englishtranslation.backend.sources;

import java.util.Locale;

/**
 * A new word or phrase found in a book.
 *
 * @param kind    "word" or "phrase"
 * @param text    the base form, lower case ("give up", "ephemeral")
 * @param times   how many times it was seen in the file
 * @param zipf    how common it is, 0 to 8 (wordfreq Zipf scale; 0 = not in the frequency list)
 * @param snippet at most 12 words around the first place it was seen; empty after queueing
 * @param useful  true if zipf is at least {@link SourceAnalyzer#USEFUL_ZIPF}
 */
public record SourceCandidate(String kind, String text, int times, double zipf, String snippet, boolean useful) {

    public static final String WORD = "word";
    public static final String PHRASE = "phrase";

    /** One line for the database: kind, text, times, zipf, snippet, separated by tabs (the other fields have none). */
    String toLine() {
        return String.join("\t", kind, text, String.valueOf(times), String.format(Locale.ROOT, "%.2f", zipf),
                snippet.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
    }

    static SourceCandidate fromLine(String line) {
        String[] parts = line.split("\t", -1);
        double zipf = Double.parseDouble(parts[3]);
        return new SourceCandidate(parts[0], parts[1], Integer.parseInt(parts[2]), zipf,
                parts.length > 4 ? parts[4] : "", zipf >= SourceAnalyzer.USEFUL_ZIPF);
    }

    SourceCandidate withoutSnippet() {
        return new SourceCandidate(kind, text, times, zipf, "", useful);
    }
}
