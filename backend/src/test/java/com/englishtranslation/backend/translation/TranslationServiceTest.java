package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.NotEnglishException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.claude.api-key=test-key")
class TranslationServiceTest {

    @Autowired
    TranslationService service;

    @Autowired
    SentenceTranslationRepository repository;

    @Autowired
    ClaudeProperties properties;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    /** The English part Claude writes since prompt v10: no translation. The text marks which answer it is. */
    private static Explanation sample(String meaning) {
        return new Explanation(
                meaning,
                List.of(new Explanation.Word("reluctant", "adjective", "not wanting to do something")),
                "");
    }

    @Test
    void secondRequestComesFromCacheWithoutCallingClaude() {
        when(claude.explainSentence("I love you")).thenReturn(sample("Anh yêu em"));

        TranslationResponse first = service.translate("I love you", TargetLanguage.VI);
        TranslationResponse second = service.translate("  I   love you ", TargetLanguage.VI);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        verify(claude, times(1)).explainSentence("I love you");
    }

    @Test
    void cachedExplanationIsReadBackTheSame() {
        Explanation explanation = sample("Anh yêu em");
        when(claude.explainSentence("I love you")).thenReturn(explanation);

        service.translate("I love you", TargetLanguage.VI);
        TranslationResponse cached = service.translate("I love you", TargetLanguage.VI);

        // The saved row has no translation; the language line is added from line_translation (a miss here is null).
        assertThat(cached.explanation().meaning()).isEqualTo(explanation.meaning());
        assertThat(cached.explanation().words()).isEqualTo(explanation.words());
    }

    @Test
    void fixIsSavedAndReadBackFromTheCache() {
        Explanation explanation = new Explanation("You finished it before now.", List.of(), "I've already done it.",
                "Use \"have done\" for a finished action that matters now.", "");
        when(claude.explainSentence("I already do it.")).thenReturn(explanation);

        TranslationResponse first = service.translate("I already do it.", TargetLanguage.EN);
        TranslationResponse cached = service.translate("i already do it", TargetLanguage.EN);

        assertThat(first.explanation().better()).isEqualTo("I've already done it.");
        assertThat(cached.cached()).isTrue();
        assertThat(cached.explanation().better()).isEqualTo("I've already done it.");
        assertThat(cached.explanation().betterWhy()).isEqualTo("Use \"have done\" for a finished action that matters now.");
    }

    @Test
    void rewriteOfCorrectTextIsDroppedBeforeItIsSaved() {
        String text = "The committee postponed its decision until further evidence was available.";
        when(claude.explainSentence(text)).thenReturn(new Explanation("They will decide later.", List.of(),
                "The group waited to decide until they had more proof.", "It sounds better.", ""));

        TranslationResponse first = service.translate(text, TargetLanguage.EN);
        TranslationResponse cached = service.translate(text, TargetLanguage.EN);

        assertThat(first.explanation().better()).isNull();
        assertThat(first.explanation().betterWhy()).isNull();
        assertThat(cached.explanation().better()).isNull();
    }

    @Test
    void textThatIsNotEnglishIsNotCachedAndAsksClaudeAgainNextTime() {
        when(claude.explainSentence("for (int i = 0; i < n; i++) {}"))
                .thenThrow(new NotEnglishException());

        assertThatThrownBy(() -> service.translate("for (int i = 0; i < n; i++) {}", TargetLanguage.VI))
                .isInstanceOf(NotEnglishException.class)
                .hasMessage("This doesn't look like English. Type an English word or sentence.");
        assertThatThrownBy(() -> service.translate("for (int i = 0; i < n; i++) {}", TargetLanguage.VI))
                .isInstanceOf(NotEnglishException.class);

        assertThat(repository.count()).isZero();
        verify(claude, times(2)).explainSentence("for (int i = 0; i < n; i++) {}");
    }

    @Test
    void personalSentenceIsExplainedButNeverSavedOrReadFromCache() {
        String text = "Email me at john@example.com";
        when(claude.explainSentence(text)).thenReturn(sample("Gui email cho toi"));

        TranslationResponse first = service.translate(text, TargetLanguage.VI);
        TranslationResponse second = service.translate(text, TargetLanguage.VI);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isFalse();
        assertThat(first.explanation().meaning()).isEqualTo("Gui email cho toi");
        assertThat(repository.count()).isZero();
        // Claude was asked both times, so no cache read happened.
        verify(claude, times(2)).explainSentence(text);
    }

    @Test
    void personalSentenceIgnoresARowThatIsAlreadyInTheCache() {
        String text = "My phone is 0912 345 678";
        String key = CacheKeys.sentenceKey(properties.sentencePromptVersion(), CacheKeys.normalizeSentence(text));
        repository.saveAndFlush(new SentenceTranslation(key, text, "en", "old meaning", "[]", "",
                properties.model(), properties.sentencePromptVersion()));
        when(claude.explainSentence(text)).thenReturn(sample("fresh"));

        TranslationResponse response = service.translate(text, TargetLanguage.VI);

        assertThat(response.explanation().meaning()).isEqualTo("fresh");
        assertThat(response.cached()).isFalse();
    }

    @Test
    void sentenceWithSmallNumbersStillCaches() {
        when(claude.explainSentence("I have 2 cats in 2026")).thenReturn(sample("Toi co 2 meo"));

        service.translate("I have 2 cats in 2026", TargetLanguage.VI);
        TranslationResponse second = service.translate("I have 2 cats in 2026", TargetLanguage.VI);

        assertThat(second.cached()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    /** Saves an English part. {@code meaning} marks the row; since v10 a row has no language and no translation. */
    private void saveRow(String text, String lang, String meaning) {
        String version = properties.sentencePromptVersion();
        String key = CacheKeys.sentenceKey(version, CacheKeys.normalizeSentence(text));
        repository.saveAndFlush(new SentenceTranslation(key, text, "en", meaning, "[]", "",
                properties.model(), version));
    }

    @Test
    void looseTextFindsTheSavedSentenceWithoutCallingClaude() {
        saveRow("How are you?", "vi", "Ban khoe khong?");

        TranslationResponse response = service.translate("  how   are you ", TargetLanguage.VI);

        assertThat(response.cached()).isTrue();
        assertThat(response.text()).isEqualTo("how are you");
        assertThat(response.explanation().meaning()).isEqualTo("Ban khoe khong?");
        verify(claude, never()).explainSentence(anyString());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void looksGoodWithAnExclamationMarkFindsASavedLooksGood() {
        saveRow("looks good", "vi", "trong on day");

        TranslationResponse response = service.translate("Looks good!", TargetLanguage.VI);

        assertThat(response.cached()).isTrue();
        // The text shown keeps what the user typed; only the lookup is loose.
        assertThat(response.text()).isEqualTo("Looks good!");
        assertThat(response.explanation().meaning()).isEqualTo("trong on day");
        verify(claude, never()).explainSentence(anyString());
    }

    @Test
    void curlyApostropheEllipsisAndCommaStillMatch() {
        saveRow("I'm fine", "vi", "toi on");

        assertThat(service.translate("I\u2019m fine\u2026", TargetLanguage.VI).cached()).isTrue();
        assertThat(service.translate("i'm fine,", TargetLanguage.VI).cached()).isTrue();
        verify(claude, never()).explainSentence(anyString());
    }

    @Test
    void aQuestionWithoutItsQuestionMarkIsTheSameQuestion() {
        // Decision 2026-10-22 (doc-7): the final "?" does not matter; "Is it OK" is typed as the same question.
        saveRow("Is it OK?", "vi", "co on khong?");

        TranslationResponse response = service.translate("Is it OK", TargetLanguage.VI);

        assertThat(response.cached()).isTrue();
        assertThat(response.text()).isEqualTo("Is it OK");
        verify(claude, never()).explainSentence(anyString());
    }

    @Test
    void wordsThatDifferAreNotMatched() {
        saveRow("Looks good!", "vi", "x");
        when(claude.explainSentence("Looks bad!")).thenReturn(sample("y"));

        assertThat(service.translate("Looks bad!", TargetLanguage.VI).cached()).isFalse();
    }

    @Test
    void exactTextWinsOverLooseText() {
        saveRow("US", "vi", "My");
        saveRow("us", "vi", "chung toi");

        assertThat(service.translate("us", TargetLanguage.VI).explanation().meaning()).isEqualTo("chung toi");
        assertThat(service.translate("US", TargetLanguage.VI).explanation().meaning()).isEqualTo("My");
        verify(claude, never()).explainSentence(anyString());
    }

    @Test
    void looseHitPicksTheOldestRowWhenSeveralShareTheKey() {
        saveRow("Hello there!", "vi", "first");
        saveRow("Hello there.", "vi", "second");

        TranslationResponse response = service.translate("hello there", TargetLanguage.VI);

        assertThat(response.explanation().meaning()).isEqualTo("first");
    }

    @Test
    void looseTextIsMatchedInAnotherLanguageToo() {
        // Since v10 the English part has no language, so a text saved for one language serves all.
        saveRow("How are you?", "vi", "A greeting.");

        TranslationResponse response = service.translate("how are you", TargetLanguage.JA);

        assertThat(response.cached()).isTrue();
        assertThat(response.explanation().meaning()).isEqualTo("A greeting.");
        verify(claude, never()).explainSentence("how are you");
    }

    @Test
    void personalTextIsNeverMatchedLoosely() {
        saveRow("Email me at john@example.com.", "vi", "old");
        when(claude.explainSentence("email me at john@example.com")).thenReturn(sample("fresh"));

        TranslationResponse response = service.translate("email me at john@example.com", TargetLanguage.VI);

        assertThat(response.cached()).isFalse();
        assertThat(response.explanation().meaning()).isEqualTo("fresh");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void textThatIsOnlyPunctuationDoesNotMatchAnything() {
        saveRow("Really?!", "vi", "that one");
        when(claude.explainSentence("?!")).thenReturn(sample("none"));

        assertThat(service.translate("?!", TargetLanguage.VI).cached()).isFalse();
    }

    @Test
    void newRowsSaveTheirMatchKey() {
        when(claude.explainSentence("How are you?")).thenReturn(sample("x"));

        service.translate("How are you?", TargetLanguage.VI);

        String expected = CacheKeys.key("sentence-loose", properties.sentencePromptVersion(), "how are you");
        assertThat(repository.findAll()).singleElement().extracting(SentenceTranslation::getMatchKey).isEqualTo(expected);
    }

    @Test
    void streamingAlsoUsesTheLooseMatch() {
        saveRow("How are you?", "vi", "Ban khoe khong?");
        saveRow("Looks \"good\"", "vi", "trong on");

        TranslationResponse response = service.translateStreaming("how are you", TargetLanguage.VI, meaning -> {
        });
        TranslationResponse curly = service.translateStreaming("Looks \u201Cgood\u201D\u2026", TargetLanguage.VI,
                meaning -> {
                });

        assertThat(response.cached()).isTrue();
        assertThat(curly.cached()).isTrue();
        assertThat(curly.text()).isEqualTo("Looks \u201Cgood\u201D\u2026");
        verify(claude, never()).explainSentence(anyString());
    }

    @Test
    void allLanguagesShareOneEnglishRow() {
        when(claude.explainSentence("I love you")).thenReturn(sample("Love for someone."));

        TranslationResponse vietnamese = service.translate("I love you", TargetLanguage.VI);
        TranslationResponse japanese = service.translate("I love you", TargetLanguage.JA);

        assertThat(vietnamese.explanation().meaning()).isEqualTo("Love for someone.");
        assertThat(japanese.explanation().meaning()).isEqualTo("Love for someone.");
        assertThat(japanese.cached()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
        verify(claude, times(1)).explainSentence("I love you");
    }
}
