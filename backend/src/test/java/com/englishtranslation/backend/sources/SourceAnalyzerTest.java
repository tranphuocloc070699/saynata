package com.englishtranslation.backend.sources;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishtranslation.backend.word.EnglishWords;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** SourceAnalyzer on small texts. Word frequency and the "already in the database" check are fakes. */
class SourceAnalyzerTest {

    /** Everything is known, except these forms (the new words of the test text) and the phrase list. */
    static final Set<String> NEW_FORMS = Set.of("wander", "wandered", "ephemeral", "suddenly", "giraffe", "giraffes");

    static final KnownLookup LOOKUP = new KnownLookup() {
        @Override
        public Set<String> knownWords(Collection<String> words) {
            return words.stream().filter(word -> !NEW_FORMS.contains(word)).collect(Collectors.toSet());
        }

        @Override
        public Set<String> knownPhrases(Collection<String> phrases) {
            return phrases.stream().filter(phrase -> phrase.equals("look up")).collect(Collectors.toSet());
        }
    };

    final SourceAnalyzer analyzer = new SourceAnalyzer(new EnglishWords(),
            new ZipfList(Map.of("wander", 3.6, "wandered", 3.4, "ephemeral", 2.5, "suddenly", 4.6, "giraffe", 3.3,
                    "giraffes", 2.9, "tall", 4.0)),
            new ZipfList(Map.of("give up", 5.6, "look up", 5.2, "kick the bucket", 4.0)));

    SourceAnalyzer.Analysis analyze(String text) {
        return analyzer.analyze(text, LOOKUP);
    }

    Optional<SourceCandidate> find(SourceAnalyzer.Analysis analysis, String kind, String text) {
        return analysis.candidates().stream().filter(c -> c.kind().equals(kind) && c.text().equals(text)).findFirst();
    }

    @Test
    void newWordsAreBaseFormsWithTheirCount() {
        var analysis = analyze("Alice wandered by the river. Giraffes are tall. The giraffe was tall too. "
                + "An ephemeral light faded. Suddenly it rained.");

        assertThat(find(analysis, "word", "wander")).hasValueSatisfying(c -> {
            assertThat(c.times()).isEqualTo(1);
            assertThat(c.useful()).isTrue();
        });
        // "Giraffes" (sentence start) and "giraffe" are one word.
        assertThat(find(analysis, "word", "giraffe")).hasValueSatisfying(c -> assertThat(c.times()).isEqualTo(2));
        assertThat(find(analysis, "word", "giraffes")).isEmpty();
        // Rare: Zipf below 3.
        assertThat(find(analysis, "word", "ephemeral")).hasValueSatisfying(c -> {
            assertThat(c.useful()).isFalse();
            assertThat(c.zipf()).isEqualTo(2.5);
        });
        // A capital letter at the start of a sentence does not make a name.
        assertThat(find(analysis, "word", "suddenly")).isPresent();
        // Known words are counted, not listed.
        assertThat(find(analysis, "word", "river")).isEmpty();
        assertThat(analysis.known()).isGreaterThan(5);
    }

    @Test
    void namesAreSkippedAndListedAsNames() {
        var analysis = analyze("Alice came home. Then Alice laughed. The dog of John's friend barked at London. The dog came back.");

        assertThat(analysis.namesSkipped()).isEqualTo(3);
        List<String> names = analysis.candidates().stream().filter(c -> c.kind().equals("name"))
                .map(SourceCandidate::text).toList();
        assertThat(names).containsExactlyInAnyOrder("alice", "john", "london");
        assertThat(find(analysis, "word", "alice")).isEmpty();
    }

    @Test
    void aWordThatIsNotEnglishIsDropped() {
        var analysis = analyze("the xqzvtk was here. the xqzvtk left.");

        assertThat(analysis.candidates().stream().filter(c -> !c.kind().equals("name"))).isEmpty();
        assertThat(analysis.namesSkipped()).isZero(); // seen only in lower case, so not a name
    }

    @Test
    void phrasesMatchWithAnInflectedFirstWordAndSkipKnownOnes() {
        var analysis = analyze("She gave up the search. He will give up too. They look up at the sky. "
                + "He gave. Up he went. To kick the bucket is sad.");

        assertThat(find(analysis, "phrase", "give up")).hasValueSatisfying(c -> {
            assertThat(c.times()).isEqualTo(2);
            assertThat(c.useful()).isTrue();
            assertThat(c.zipf()).isEqualTo(5.6);
        });
        assertThat(find(analysis, "phrase", "kick the bucket")).isPresent();
        assertThat(find(analysis, "phrase", "look up")).isEmpty(); // already cached
    }

    @Test
    void aPhraseNeedsNeighbourWordsOnTheSameLine() {
        var analysis = analyze("He will give.\nUp the hill he went. Please give, up to you.");

        assertThat(find(analysis, "phrase", "give up")).isEmpty();
    }

    @Test
    void theSnippetHasAtMostTwelveWordsAndStopsAtTheSentenceEnd() {
        String text = "One two three four five six seven eight nine ten eleven twelve ephemeral thirteen fourteen "
                + "fifteen sixteen seventeen eighteen.";
        var analysis = analyze(text);

        String snippet = find(analysis, "word", "ephemeral").orElseThrow().snippet();
        assertThat(snippet.split(" ")).hasSizeLessThanOrEqualTo(12);
        assertThat(snippet).contains("ephemeral");

        var second = analyze("Rain fell. A sad ephemeral dream.\nNext line.");
        assertThat(find(second, "word", "ephemeral").orElseThrow().snippet()).isEqualTo("A sad ephemeral dream.");
    }

    @Test
    void candidatesAreSortedByTimesThenCommonness() {
        var analysis = analyze("Giraffes are tall. The giraffe was tall too. An ephemeral light. Suddenly it rained.");

        List<String> words = analysis.candidates().stream().filter(c -> c.kind().equals("word"))
                .map(SourceCandidate::text).toList();
        assertThat(words).containsExactly("giraffe", "suddenly", "ephemeral");
    }

    @Test
    void aCandidateSurvivesTheDatabaseLine() {
        var candidate = new SourceCandidate("phrase", "give up", 3, 5.6, "she gave\tup\nnow", true);

        var back = SourceCandidate.fromLine(candidate.toLine());

        assertThat(back.text()).isEqualTo("give up");
        assertThat(back.times()).isEqualTo(3);
        assertThat(back.useful()).isTrue();
        assertThat(back.snippet()).isEqualTo("she gave up now");
        assertThat(back.withoutSnippet().snippet()).isEmpty();
    }
}
