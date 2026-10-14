package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
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
