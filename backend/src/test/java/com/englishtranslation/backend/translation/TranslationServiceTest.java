package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.claude.ClaudeService;
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
