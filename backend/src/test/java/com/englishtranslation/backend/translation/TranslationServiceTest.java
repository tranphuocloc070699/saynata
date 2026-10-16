package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static Explanation sample(String translation) {
        return new Explanation(
                "The speaker did not want to go.",
                List.of(new Explanation.Word("reluctant", "adjective", "not wanting to do something")),
                translation);
    }

    @Test
    void secondRequestComesFromCacheWithoutCallingClaude() {
        when(claude.explainSentence("I love you", TargetLanguage.VI)).thenReturn(sample("Anh yêu em"));

        TranslationResponse first = service.translate("I love you", TargetLanguage.VI);
        TranslationResponse second = service.translate("  I   love you ", TargetLanguage.VI);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        verify(claude, times(1)).explainSentence("I love you", TargetLanguage.VI);
    }

    @Test
    void cachedExplanationIsReadBackTheSame() {
        Explanation explanation = sample("Anh yêu em");
        when(claude.explainSentence("I love you", TargetLanguage.VI)).thenReturn(explanation);

        service.translate("I love you", TargetLanguage.VI);
        TranslationResponse cached = service.translate("I love you", TargetLanguage.VI);

        assertThat(cached.explanation()).isEqualTo(explanation);
    }

    @Test
    void textThatIsNotEnglishIsNotCachedAndAsksClaudeAgainNextTime() {
        when(claude.explainSentence("for (int i = 0; i < n; i++) {}", TargetLanguage.VI))
                .thenThrow(new NotEnglishException());

        assertThatThrownBy(() -> service.translate("for (int i = 0; i < n; i++) {}", TargetLanguage.VI))
                .isInstanceOf(NotEnglishException.class)
                .hasMessage("This doesn't look like English. Type an English word or sentence.");
        assertThatThrownBy(() -> service.translate("for (int i = 0; i < n; i++) {}", TargetLanguage.VI))
                .isInstanceOf(NotEnglishException.class);

        assertThat(repository.count()).isZero();
        verify(claude, times(2)).explainSentence("for (int i = 0; i < n; i++) {}", TargetLanguage.VI);
    }

    @Test
    void personalSentenceIsExplainedButNeverSavedOrReadFromCache() {
        String text = "Email me at john@example.com";
        when(claude.explainSentence(text, TargetLanguage.VI)).thenReturn(sample("Gui email cho toi"));

        TranslationResponse first = service.translate(text, TargetLanguage.VI);
        TranslationResponse second = service.translate(text, TargetLanguage.VI);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isFalse();
        assertThat(first.explanation().translation()).isEqualTo("Gui email cho toi");
        assertThat(repository.count()).isZero();
        // Claude was asked both times, so no cache read happened.
        verify(claude, times(2)).explainSentence(text, TargetLanguage.VI);
    }

    @Test
    void personalSentenceIgnoresARowThatIsAlreadyInTheCache() {
        String text = "My phone is 0912 345 678";
        String key = CacheKeys.key("sentence", properties.sentencePromptVersion(), "vi", CacheKeys.normalizeSentence(text));
        repository.saveAndFlush(new SentenceTranslation(key, text, "vi", "old meaning", "[]", "old",
                properties.model(), properties.sentencePromptVersion()));
        when(claude.explainSentence(text, TargetLanguage.VI)).thenReturn(sample("fresh"));

        TranslationResponse response = service.translate(text, TargetLanguage.VI);

        assertThat(response.explanation().translation()).isEqualTo("fresh");
        assertThat(response.cached()).isFalse();
    }

    @Test
    void sentenceWithSmallNumbersStillCaches() {
        when(claude.explainSentence("I have 2 cats in 2026", TargetLanguage.VI)).thenReturn(sample("Toi co 2 meo"));

        service.translate("I have 2 cats in 2026", TargetLanguage.VI);
        TranslationResponse second = service.translate("I have 2 cats in 2026", TargetLanguage.VI);

        assertThat(second.cached()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    private void saveRow(String text, String lang, String translation) {
        String version = properties.sentencePromptVersion();
        String key = CacheKeys.key("sentence", version, lang, CacheKeys.normalizeSentence(text));
        repository.saveAndFlush(new SentenceTranslation(key, text, lang, "meaning of " + text, "[]", translation,
                properties.model(), version));
    }

    @Test
    void looseTextFindsTheSavedSentenceWithoutCallingClaude() {
        saveRow("How are you?", "vi", "Ban khoe khong?");

        TranslationResponse response = service.translate("  how   are you ", TargetLanguage.VI);

        assertThat(response.cached()).isTrue();
        assertThat(response.text()).isEqualTo("how are you");
        assertThat(response.explanation().translation()).isEqualTo("Ban khoe khong?");
        verifyNoInteractions(claude);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void exactTextWinsOverLooseText() {
        saveRow("US", "vi", "My");
        saveRow("us", "vi", "chung toi");

        assertThat(service.translate("us", TargetLanguage.VI).explanation().translation()).isEqualTo("chung toi");
        assertThat(service.translate("US", TargetLanguage.VI).explanation().translation()).isEqualTo("My");
        verifyNoInteractions(claude);
    }

    @Test
    void looseHitPicksTheOldestRowWhenSeveralShareTheKey() {
        saveRow("Hello there!", "vi", "first");
        saveRow("Hello there.", "vi", "second");

        TranslationResponse response = service.translate("hello there", TargetLanguage.VI);

        assertThat(response.explanation().translation()).isEqualTo("first");
    }

    @Test
    void looseTextOfAnotherLanguageIsNotMatched() {
        saveRow("How are you?", "vi", "Ban khoe khong?");
        when(claude.explainSentence("how are you", TargetLanguage.JA)).thenReturn(sample("ogenki desu ka"));

        TranslationResponse response = service.translate("how are you", TargetLanguage.JA);

        assertThat(response.cached()).isFalse();
        verify(claude).explainSentence("how are you", TargetLanguage.JA);
    }

    @Test
    void personalTextIsNeverMatchedLoosely() {
        saveRow("Email me at john@example.com.", "vi", "old");
        when(claude.explainSentence("email me at john@example.com", TargetLanguage.VI)).thenReturn(sample("fresh"));

        TranslationResponse response = service.translate("email me at john@example.com", TargetLanguage.VI);

        assertThat(response.cached()).isFalse();
        assertThat(response.explanation().translation()).isEqualTo("fresh");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void textThatIsOnlyPunctuationDoesNotMatchAnything() {
        saveRow("Really?!", "vi", "that one");
        when(claude.explainSentence("?!", TargetLanguage.VI)).thenReturn(sample("none"));

        assertThat(service.translate("?!", TargetLanguage.VI).cached()).isFalse();
    }

    @Test
    void newRowsSaveTheirMatchKey() {
        when(claude.explainSentence("How are you?", TargetLanguage.VI)).thenReturn(sample("x"));

        service.translate("How are you?", TargetLanguage.VI);

        String expected = CacheKeys.key("sentence-loose", properties.sentencePromptVersion(), "vi", "how are you");
        assertThat(repository.findAll()).singleElement().extracting(SentenceTranslation::getMatchKey).isEqualTo(expected);
    }

    @Test
    void streamingAlsoUsesTheLooseMatch() {
        saveRow("How are you?", "vi", "Ban khoe khong?");

        TranslationResponse response = service.translateStreaming("how are you", TargetLanguage.VI, meaning -> {
        });

        assertThat(response.cached()).isTrue();
        verifyNoInteractions(claude);
    }

    @Test
    void eachLanguageIsCachedSeparately() {
        when(claude.explainSentence("I love you", TargetLanguage.VI)).thenReturn(sample("Anh yêu em"));
        when(claude.explainSentence("I love you", TargetLanguage.JA)).thenReturn(sample("愛してる"));

        TranslationResponse vietnamese = service.translate("I love you", TargetLanguage.VI);
        TranslationResponse japanese = service.translate("I love you", TargetLanguage.JA);

        assertThat(vietnamese.explanation().translation()).isEqualTo("Anh yêu em");
        assertThat(japanese.explanation().translation()).isEqualTo("愛してる");
        assertThat(japanese.cached()).isFalse();
        assertThat(repository.count()).isEqualTo(2);
    }
}
