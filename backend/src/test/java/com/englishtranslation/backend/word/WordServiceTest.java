package com.englishtranslation.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.claude.api-key=test-key")
class WordServiceTest {

    @Autowired
    WordService service;

    @Autowired
    WordLookupRepository repository;

    @Autowired
    EnglishWords englishWords;

    @Autowired
    IpaDictionary ipaDictionary;

    @Autowired
    SimpleWords simpleWords;

    @Autowired
    ClaudeProperties properties;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void secondLookupComesFromCacheWithoutCallingClaude() {
        when(claude.describeWord("reluctant")).thenReturn(new WordEntry(List.of()));

        WordResponse first = service.lookUp("reluctant");
        WordResponse second = service.lookUp("  Reluctant ");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        verify(claude, times(1)).describeWord("reluctant");
    }

    @Test
    void savedEntryIsReadBackTheSame() {
        WordEntry entry = new WordEntry(List.of(new WordEntry.Sense(
                "adjective", "C1", "not wanting to do something", "miễn cưỡng",
                List.of(new WordEntry.Example("easy", "She was reluctant to leave."),
                        new WordEntry.Example("medium", "He gave a reluctant smile."),
                        new WordEntry.Example("harder", "They were reluctant to accept the offer.")),
                List.of(new WordEntry.Usage("pattern", "be reluctant to + verb", "She was reluctant to leave.")))));
        when(claude.describeWord("reluctant")).thenReturn(entry);

        WordResponse first = service.lookUp("Reluctant");
        WordResponse second = service.lookUp("reluctant");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.entry()).isEqualTo(entry);
        verify(claude, times(1)).describeWord("reluctant");
    }

    @Test
    void wordNotOnTheListIsRejectedWithoutCallingClaudeOrSaving() {
        WordResponse response = service.lookUp("runk");

        assertThat(response.word()).isEqualTo("runk");
        assertThat(response.entry().senses()).isEmpty();
        assertThat(response.cached()).isFalse();
        assertThat(response.ipa()).isNull();
        verify(claude, never()).describeWord(anyString());
        assertThat(repository.count()).isZero();
    }

    @Test
    void realRareWordCallsClaude() {
        when(claude.describeWord("serendipity")).thenReturn(new WordEntry(List.of()));

        service.lookUp("serendipity");

        verify(claude, times(1)).describeWord("serendipity");
    }

    @Test
    void baseAndInflectedFormsAreAllowedAndUppercaseWorks() {
        when(claude.describeWord(anyString())).thenReturn(new WordEntry(List.of()));

        service.lookUp("run");
        service.lookUp("running");
        service.lookUp("SERENDIPITY");

        verify(claude).describeWord("run");
        verify(claude).describeWord("running");
        verify(claude).describeWord("serendipity");
    }

    @Test
    void wordListHasScowlAndNgslWords() {
        assertThat(englishWords.contains("serendipity")).isTrue();
        assertThat(englishWords.contains("ran")).isTrue();
        assertThat(englishWords.contains("runk")).isFalse();
        assertThat(englishWords.size()).isGreaterThan(100_000);
    }

    @Test
    void wordWithIpaOnTheListGetsIt() {
        when(claude.describeWord("graveyard")).thenReturn(new WordEntry(List.of()));

        WordResponse response = service.lookUp("Graveyard");

        assertThat(response.ipa()).isEqualTo("/ˈɡɹeɪvˌjɑɹd/");
    }

    @Test
    void realWordMissingFromTheIpaListGetsNullIpa() {
        // "aardwolf" is on the SCOWL word list but not on the IPA list.
        when(claude.describeWord("aardwolf")).thenReturn(new WordEntry(List.of()));

        WordResponse response = service.lookUp("aardwolf");

        assertThat(englishWords.contains("aardwolf")).isTrue();
        assertThat(response.ipa()).isNull();
        assertThat(response.entry()).isNotNull();
    }

    @Test
    void rowSavedBeforeIpaExistedGetsIpaWhenReadFromCache() {
        // A row as it was saved before this feature: the entry JSON has no IPA.
        String key = CacheKeys.key("word", properties.wordPromptVersion(), "apple");
        repository.saveAndFlush(new WordLookup(
                key, "apple", "{\"senses\":[]}", properties.model(), properties.wordPromptVersion()));

        WordResponse response = service.lookUp("apple");

        assertThat(response.cached()).isTrue();
        assertThat(response.ipa()).isEqualTo("/ˈæpəɫ/");
        verify(claude, never()).describeWord(anyString());
    }

    @Test
    void ipaListLoadsTheFirstIpaOfAWordWithManyAndIgnoresCase() {
        assertThat(ipaDictionary.ipaFor("hello")).isEqualTo("/həˈɫoʊ/");
        assertThat(ipaDictionary.ipaFor("HELLO")).isEqualTo("/həˈɫoʊ/");
        assertThat(ipaDictionary.ipaFor("qwzxv")).isNull();
        assertThat(ipaDictionary.size()).isGreaterThan(100_000);
    }

    private static WordEntry entryWith(String definition, String easyExample) {
        return new WordEntry(List.of(new WordEntry.Sense(
                "adjective", "C1", definition, "miễn cưỡng",
                List.of(new WordEntry.Example("easy", easyExample),
                        new WordEntry.Example("medium", "He gave a reluctant smile."),
                        new WordEntry.Example("harder", "They were reluctant to accept the offer.")),
                List.of())));
    }

    private boolean savedNeedsReview(String word) {
        String key = CacheKeys.key("word", properties.wordPromptVersion(), word);
        return repository.findByCacheKey(key).orElseThrow().isNeedsReview();
    }

    @Test
    void simpleEntryIsSavedWithoutRetry() {
        when(claude.describeWord("reluctant")).thenReturn(entryWith("not wanting to do something", "She was reluctant to leave."));

        service.lookUp("reluctant");

        verify(claude, never()).describeWordAgain(anyString(), anyString());
        assertThat(savedNeedsReview("reluctant")).isFalse();
    }

    @Test
    void hardDefinitionTriggersExactlyOneRetryWithFeedbackAndTheBetterEntryIsSaved() {
        WordEntry hard = entryWith("unwilling and hesitant", "She was reluctant to leave.");
        WordEntry simple = entryWith("not wanting to do something", "She was reluctant to leave.");
        when(claude.describeWord("reluctant")).thenReturn(hard);
        when(claude.describeWordAgain(anyString(), anyString())).thenReturn(simple);

        WordResponse response = service.lookUp("reluctant");

        verify(claude, times(1)).describeWord("reluctant");
        verify(claude, times(1)).describeWordAgain(Mockito.eq("reluctant"), contains("hesitant"));
        assertThat(response.entry()).isEqualTo(simple);
        assertThat(savedNeedsReview("reluctant")).isFalse();
    }

    @Test
    void secondFailureIsSavedAndFlaggedWithoutAThirdCall() {
        WordEntry hard = entryWith("unwilling and hesitant", "She was reluctant to leave.");
        when(claude.describeWord("reluctant")).thenReturn(hard);
        when(claude.describeWordAgain(anyString(), anyString())).thenReturn(hard);

        WordResponse response = service.lookUp("reluctant");

        verify(claude, times(1)).describeWord("reluctant");
        verify(claude, times(1)).describeWordAgain(anyString(), anyString());
        assertThat(response.entry()).isEqualTo(hard);
        assertThat(savedNeedsReview("reluctant")).isTrue();
    }

    @Test
    void failedRetryKeepsTheFirstEntryAndFlagsIt() {
        WordEntry hard = entryWith("unwilling and hesitant", "She was reluctant to leave.");
        when(claude.describeWord("reluctant")).thenReturn(hard);
        when(claude.describeWordAgain(anyString(), anyString())).thenThrow(new IllegalStateException("down"));

        WordResponse response = service.lookUp("reluctant");

        assertThat(response.entry()).isEqualTo(hard);
        assertThat(savedNeedsReview("reluctant")).isTrue();
    }

    @Test
    void possessivesAndContractionsAreNotHardWords() {
        assertThat(simpleWords.hardWordsInDefinition("a person's name, or something you don't know")).isEmpty();
        assertThat(simpleWords.hardWordsInExample("I'm sure they've said it isn't hers. We can't go, oh my.", "x")).isEmpty();
        assertThat(simpleWords.hardWordsInDefinition("unwilling and hesitant")).containsExactly("hesitant", "unwilling");
    }

    @Test
    void exampleLimitsDependOnTheLevelAndTheHeadwordIsAllowed() {
        // easy: 0 hard words allowed, medium: 1, harder: 2. The headword does not count.
        WordEntry.Sense sense = new WordEntry.Sense("noun", "B1", "a place to sit", "ghế",
                List.of(new WordEntry.Example("easy", "The bench is old."),
                        new WordEntry.Example("medium", "The bench is dilapidated."),
                        new WordEntry.Example("harder", "The dilapidated bench was unstable.")),
                List.of());

        assertThat(simpleWords.problems(new WordEntry(List.of(sense)), "bench")).isEmpty();

        WordEntry.Sense tooHard = new WordEntry.Sense("noun", "B1", "a place to sit", "ghế",
                List.of(new WordEntry.Example("easy", "The bench is dilapidated.")), List.of());
        assertThat(simpleWords.problems(new WordEntry(List.of(tooHard)), "bench")).hasSize(1);
    }
}
