package com.englishtranslation.backend.sources;

import com.englishtranslation.backend.prefill.SentenceWordQueue;
import com.englishtranslation.backend.word.EnglishWords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Finds the new words and phrases in the text of a book (TASK-108). No LLM, no database write: the text goes in,
 * counts and candidates come out, and the text is forgotten.
 *
 * <ul>
 * <li><b>Names:</b> a word is a name when it is never written in lower case and is capitalised in the middle of a
 * sentence, or is not a real word at all. Names are listed (up to {@link #MAX_NAMES}) but never queued.</li>
 * <li><b>Base form:</b> a form like "wandering" becomes "wander" with the same endings the prefill code uses
 * ({@link SentenceWordQueue#candidateBases}); the base wins when it is about as common as the form. A word whose
 * form or base is already known (bundled, cached, queued) is counted as known.</li>
 * <li><b>Phrases:</b> two to four words in a row that are on the phrasal verb and idiom list. The first word may be
 * a form ("gave up" matches "give up").</li>
 * <li><b>Useful or rare:</b> by Zipf, {@link #USEFUL_ZIPF} or more is useful.</li>
 * </ul>
 */
@Component
public class SourceAnalyzer {

    public static final double USEFUL_ZIPF = 3.0;
    static final int MAX_SNIPPET_WORDS = 12;
    static final int MAX_CANDIDATES = 6000;
    static final int MAX_NAMES = 500;
    private static final int MIN_WORD_LENGTH = 3;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)*");
    private static final Pattern SPACES_ONLY = Pattern.compile("[ \\t]+");
    private static final String SENTENCE_END = ".!?…";
    private static final String OPENERS = "\"'“‘([*_#>`- \t";

    /** Common irregular verb forms, only used to find the base of the first word of a phrase ("gave up"). */
    private static final Map<String, String> IRREGULAR = Map.ofEntries(
            Map.entry("gave", "give"), Map.entry("given", "give"), Map.entry("took", "take"),
            Map.entry("taken", "take"), Map.entry("went", "go"), Map.entry("gone", "go"), Map.entry("came", "come"),
            Map.entry("got", "get"), Map.entry("gotten", "get"), Map.entry("made", "make"), Map.entry("saw", "see"),
            Map.entry("seen", "see"), Map.entry("ran", "run"), Map.entry("brought", "bring"),
            Map.entry("held", "hold"), Map.entry("kept", "keep"), Map.entry("left", "leave"),
            Map.entry("fell", "fall"), Map.entry("fallen", "fall"), Map.entry("broke", "break"),
            Map.entry("broken", "break"), Map.entry("threw", "throw"), Map.entry("thrown", "throw"),
            Map.entry("wore", "wear"), Map.entry("worn", "wear"), Map.entry("stood", "stand"),
            Map.entry("sat", "sit"), Map.entry("found", "find"), Map.entry("told", "tell"),
            Map.entry("ate", "eat"), Map.entry("eaten", "eat"), Map.entry("drew", "draw"),
            Map.entry("drawn", "draw"), Map.entry("grew", "grow"), Map.entry("grown", "grow"),
            Map.entry("knew", "know"), Map.entry("known", "know"), Map.entry("spoke", "speak"),
            Map.entry("spoken", "speak"), Map.entry("woke", "wake"), Map.entry("woken", "wake"),
            Map.entry("rose", "rise"), Map.entry("risen", "rise"), Map.entry("was", "be"), Map.entry("were", "be"),
            Map.entry("is", "be"), Map.entry("are", "be"), Map.entry("had", "have"), Map.entry("has", "have"),
            Map.entry("did", "do"), Map.entry("does", "do"), Map.entry("lay", "lie"), Map.entry("bore", "bear"),
            Map.entry("sold", "sell"), Map.entry("lost", "lose"), Map.entry("paid", "pay"),
            Map.entry("met", "meet"), Map.entry("led", "lead"), Map.entry("slept", "sleep"),
            Map.entry("felt", "feel"), Map.entry("hung", "hang"), Map.entry("dug", "dig"));

    /** The result: counts of distinct forms, and the candidates (new words, new phrases, skipped names). */
    public record Analysis(int wordsInFile, int known, int namesSkipped, List<SourceCandidate> candidates) {
    }

    private final EnglishWords englishWords;
    private final ZipfList wordZipf;
    private final ZipfList phraseZipf;
    private final Set<String> phraseFirstWords = new HashSet<>();

    @Autowired
    public SourceAnalyzer(EnglishWords englishWords) {
        this(englishWords, new ZipfList("words/zipf-en.tsv"), new ZipfList("words/phrases-en.tsv"));
    }

    /** For tests: small lists made in code. */
    SourceAnalyzer(EnglishWords englishWords, ZipfList wordZipf, ZipfList phraseZipf) {
        this.englishWords = englishWords;
        this.wordZipf = wordZipf;
        this.phraseZipf = phraseZipf;
        for (String phrase : phraseZipf.keys()) {
            phraseFirstWords.add(phrase.substring(0, phrase.indexOf(' ')));
        }
    }

    // ---------- tokens ----------

    private record Token(String lower, boolean contraction, int start, int end, Kind kind) {
    }

    /** How a word was written at one place. */
    private enum Kind { LOWER, UPPER, CAP_START, CAP_MID }

    /** What we know about one lower-case form: how often and how it was written, and where it was first seen. */
    private static final class Stat {
        int times;
        int lowerOrUpper;
        int capMid;
        int firstToken = -1;
    }

    /** A candidate under construction: how often, and the first token (for the snippet). */
    private static final class Group {
        int times;
        int firstToken = Integer.MAX_VALUE;

        void add(int count, int token) {
            times += count;
            firstToken = Math.min(firstToken, token);
        }
    }

    private List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group().replace('’', '\'');
            if (raw.length() > 2 && raw.toLowerCase(Locale.ROOT).endsWith("'s")) {
                raw = raw.substring(0, raw.length() - 2);
            }
            boolean contraction = raw.indexOf('\'') >= 0;
            tokens.add(new Token(raw.toLowerCase(Locale.ROOT), contraction, matcher.start(), matcher.end(),
                    kindOf(raw, startsSentence(text, matcher.start()))));
        }
        return tokens;
    }

    private static Kind kindOf(String raw, boolean sentenceStart) {
        if (raw.length() > 1 && raw.equals(raw.toUpperCase(Locale.ROOT))) {
            return Kind.UPPER;
        }
        if (Character.isLowerCase(raw.charAt(0))) {
            return Kind.LOWER;
        }
        boolean restLower = raw.substring(1).equals(raw.substring(1).toLowerCase(Locale.ROOT));
        if (restLower && sentenceStart) {
            return Kind.CAP_START;
        }
        return Kind.CAP_MID;
    }

    /** True at the start of the text or a line, or after . ! ? (quotes and markdown marks in between are ignored). */
    private static boolean startsSentence(String text, int index) {
        int i = index - 1;
        while (i >= 0 && OPENERS.indexOf(text.charAt(i)) >= 0) {
            i--;
        }
        return i < 0 || text.charAt(i) == '\n' || text.charAt(i) == '\r' || SENTENCE_END.indexOf(text.charAt(i)) >= 0;
    }

    // ---------- analysis ----------

    public Analysis analyze(String text, KnownLookup lookup) {
        List<Token> tokens = tokenize(text);
        Map<String, Stat> stats = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.contraction() || token.lower().length() < 2) {
                continue;
            }
            Stat stat = stats.computeIfAbsent(token.lower(), key -> new Stat());
            stat.times++;
            if (stat.firstToken < 0) {
                stat.firstToken = i;
            }
            switch (token.kind()) {
                case LOWER, UPPER -> stat.lowerOrUpper++;
                case CAP_MID -> stat.capMid++;
                case CAP_START -> {
                }
            }
        }

        List<SourceCandidate> names = new ArrayList<>();
        int namesSkipped = 0;
        List<String> forms = new ArrayList<>();
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            String form = entry.getKey();
            Stat stat = entry.getValue();
            if (isName(form, stat)) {
                namesSkipped++;
                if (names.size() < MAX_NAMES) {
                    names.add(new SourceCandidate("name", form, stat.times, wordZipf.zipf(form),
                            snippet(text, tokens, stat.firstToken, 1), false));
                }
            } else {
                forms.add(form);
            }
        }

        // Words: base forms, then the known check on every form and base at once.
        Map<String, List<String>> basesOf = new HashMap<>();
        Set<String> toCheck = new HashSet<>();
        for (String form : forms) {
            List<String> bases = SentenceWordQueue.candidateBases(form).stream().filter(englishWords::contains).toList();
            basesOf.put(form, bases);
            if (englishWords.contains(form)) {
                toCheck.add(form);
            }
            toCheck.addAll(bases);
        }
        Set<String> knownNow = toCheck.isEmpty() ? Set.of() : lookup.knownWords(toCheck);

        int known = 0;
        Map<String, Group> wordGroups = new HashMap<>();
        for (String form : forms) {
            List<String> bases = basesOf.get(form);
            boolean isWord = englishWords.contains(form);
            if (!isWord && bases.isEmpty()) {
                continue; // not an English word (a typo, a foreign word, a made-up name)
            }
            if (knownNow.contains(form) || bases.stream().anyMatch(knownNow::contains)) {
                known++;
                continue;
            }
            String lemma = chooseLemma(form, isWord, bases);
            if (lemma.length() < MIN_WORD_LENGTH) {
                continue;
            }
            Stat stat = stats.get(form);
            wordGroups.computeIfAbsent(lemma, key -> new Group()).add(stat.times, stat.firstToken);
        }

        List<SourceCandidate> found = new ArrayList<>();
        for (Map.Entry<String, Group> entry : wordGroups.entrySet()) {
            found.add(candidate(SourceCandidate.WORD, entry.getKey(), wordZipf.zipf(entry.getKey()), entry.getValue(),
                    text, tokens, 1));
        }

        // Phrases.
        Map<String, Group> phraseGroups = findPhrases(text, tokens);
        if (!phraseGroups.isEmpty()) {
            Set<String> knownPhrases = lookup.knownPhrases(phraseGroups.keySet());
            phraseGroups.keySet().removeAll(knownPhrases);
        }
        for (Map.Entry<String, Group> entry : phraseGroups.entrySet()) {
            int length = entry.getKey().split(" ").length;
            found.add(candidate(SourceCandidate.PHRASE, entry.getKey(), phraseZipf.zipf(entry.getKey()),
                    entry.getValue(), text, tokens, length));
        }

        found.sort(Comparator.comparingInt(SourceCandidate::times).reversed()
                .thenComparing(Comparator.comparingDouble(SourceCandidate::zipf).reversed())
                .thenComparing(SourceCandidate::text));
        List<SourceCandidate> all = new ArrayList<>(found.size() > MAX_CANDIDATES ? found.subList(0, MAX_CANDIDATES) : found);
        names.sort(Comparator.comparingInt(SourceCandidate::times).reversed().thenComparing(SourceCandidate::text));
        all.addAll(names);
        return new Analysis(stats.size(), known, namesSkipped, all);
    }

    private SourceCandidate candidate(String kind, String text, double zipf, Group group, String source,
            List<Token> tokens, int length) {
        return new SourceCandidate(kind, text, group.times, zipf, snippet(source, tokens, group.firstToken, length),
                zipf >= USEFUL_ZIPF);
    }

    /** Never written in lower case, and either capitalised mid-sentence or not a real word. */
    private boolean isName(String form, Stat stat) {
        return stat.lowerOrUpper == 0 && (stat.capMid > 0 || !englishWords.contains(form));
    }

    /**
     * The base form to queue. A base wins when it is on the word list and about as common as the form
     * (within 1.0 Zipf): "wandering" gives "wander". A form that is not on the list uses its most common base.
     */
    private String chooseLemma(String form, boolean formIsWord, List<String> bases) {
        String bestBase = null;
        for (String base : bases) {
            if (bestBase == null || wordZipf.zipf(base) > wordZipf.zipf(bestBase)) {
                bestBase = base;
            }
        }
        if (bestBase == null) {
            return form;
        }
        if (!formIsWord) {
            return bestBase;
        }
        double baseZipf = wordZipf.zipf(bestBase);
        return baseZipf > 0 && baseZipf >= wordZipf.zipf(form) - 1.0 ? bestBase : form;
    }

    // ---------- phrases ----------

    private Map<String, Group> findPhrases(String text, List<Token> tokens) {
        Map<String, Group> found = new HashMap<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token first = tokens.get(i);
            if (first.contraction()) {
                continue;
            }
            List<String> firstChoices = firstWordChoices(first.lower());
            if (firstChoices.isEmpty()) {
                continue;
            }
            for (int length = 4; length >= 2; length--) {
                if (!isRun(text, tokens, i, length)) {
                    continue;
                }
                String rest = restOfPhrase(tokens, i + 1, length - 1);
                String match = firstChoices.stream().map(choice -> choice + " " + rest)
                        .filter(phraseZipf::contains).findFirst().orElse(null);
                if (match != null) {
                    found.computeIfAbsent(match, key -> new Group()).add(1, i);
                    break;
                }
            }
        }
        return found;
    }

    /** The first word and its possible base forms, only those that start a phrase on the list. */
    private List<String> firstWordChoices(String word) {
        Set<String> choices = new LinkedHashSet<>();
        choices.add(word);
        choices.addAll(SentenceWordQueue.candidateBases(word));
        String irregular = IRREGULAR.get(word);
        if (irregular != null) {
            choices.add(irregular);
        }
        choices.removeIf(choice -> !phraseFirstWords.contains(choice));
        return new ArrayList<>(choices);
    }

    private static String restOfPhrase(List<Token> tokens, int from, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < from + count; i++) {
            out.append(i > from ? " " : "").append(tokens.get(i).lower());
        }
        return out.toString();
    }

    /** True if {@code length} tokens from {@code from} are neighbours: only spaces between them, no contractions. */
    private static boolean isRun(String text, List<Token> tokens, int from, int length) {
        if (from + length > tokens.size()) {
            return false;
        }
        for (int i = from + 1; i < from + length; i++) {
            Token before = tokens.get(i - 1);
            Token after = tokens.get(i);
            if (after.contraction() || !SPACES_ONLY.matcher(text.substring(before.end(), after.start())).matches()) {
                return false;
            }
        }
        return true;
    }

    // ---------- snippets ----------

    private String snippet(String text, List<Token> tokens, int tokenIndex, int tokenCount) {
        if (tokenIndex < 0 || tokenIndex >= tokens.size()) {
            return "";
        }
        int start = tokens.get(tokenIndex).start();
        int end = tokens.get(Math.min(tokens.size() - 1, tokenIndex + tokenCount - 1)).end();
        return snippet(text, start, end);
    }

    /** At most {@link #MAX_SNIPPET_WORDS} words around [start, end): not across a line break or a sentence end. */
    static String snippet(String text, int start, int end) {
        int left = start;
        while (left > 0 && !Character.isWhitespace(text.charAt(left - 1))) {
            left--;
        }
        int right = end;
        while (right < text.length() && !Character.isWhitespace(text.charAt(right))) {
            right++;
        }
        int core = text.substring(left, right).trim().split("\\s+").length;
        int budget = Math.max(0, MAX_SNIPPET_WORDS - core);
        int beforeBudget = budget / 2;
        int afterBudget = budget - beforeBudget;

        int added = 0;
        while (added < beforeBudget && left > 0) {
            int p = left;
            boolean newline = false;
            while (p > 0 && Character.isWhitespace(text.charAt(p - 1))) {
                newline |= text.charAt(p - 1) == '\n' || text.charAt(p - 1) == '\r';
                p--;
            }
            if (p == 0 || newline || SENTENCE_END.indexOf(text.charAt(p - 1)) >= 0) {
                break;
            }
            while (p > 0 && !Character.isWhitespace(text.charAt(p - 1))) {
                p--;
            }
            left = p;
            added++;
        }
        added = 0;
        while (added < afterBudget && right < text.length()) {
            if (SENTENCE_END.indexOf(text.charAt(right - 1)) >= 0) {
                break;
            }
            int p = right;
            boolean newline = false;
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) {
                newline |= text.charAt(p) == '\n' || text.charAt(p) == '\r';
                p++;
            }
            if (p >= text.length() || newline) {
                break;
            }
            while (p < text.length() && !Character.isWhitespace(text.charAt(p))) {
                p++;
            }
            right = p;
            added++;
        }
        String[] words = text.substring(left, right).replaceAll("[*_#>`]", "").trim().split("\\s+");
        int count = Math.min(words.length, MAX_SNIPPET_WORDS);
        String joined = String.join(" ", Arrays.copyOfRange(words, 0, count));
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
    }
}
