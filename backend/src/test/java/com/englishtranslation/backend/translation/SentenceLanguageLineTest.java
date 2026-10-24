package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.lines.LineTranslation;
import com.englishtranslation.backend.lines.LineTranslationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * TASK-106.6: the English part of a sentence is paid once for all languages; the language line comes from
 * line_translation. Guests may make new calls here (REQUIRE_SIGN_IN=false), so the whole flow runs. Claude is a mock.
 */
@SpringBootTest(properties = { "app.claude.api-key=test-key", "app.auth.require-sign-in=false" })
class SentenceLanguageLineTest {

    private static final Explanation ENGLISH = new Explanation("A friendly hello.", List.of(), "");

    @Autowired
    TranslationService service;
    @Autowired
    SentenceTranslationRepository sentences;
    @Autowired
    LineTranslationRepository lines;
    @Autowired
    ClaudeProperties properties;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void cleanDatabase() {
        sentences.deleteAll();
        lines.deleteAll();
    }

    @Test
    void aSecondLanguageForACachedSentenceIsOneLineCallAndNoSentenceCall() {
        when(claude.explainSentence("Hello there")).thenReturn(ENGLISH);
        when(claude.translateLines(TargetLanguage.VI, List.of("Hello there"))).thenReturn(List.of("Xin chào"));
        when(claude.translateLines(TargetLanguage.JA, List.of("Hello there"))).thenReturn(List.of("こんにちは"));

        TranslationResponse vi = service.translate("Hello there", TargetLanguage.VI);
        TranslationResponse ja = service.translate("  Hello   there ", TargetLanguage.JA);

        assertThat(vi.cached()).isFalse();
        assertThat(vi.explanation().meaning()).isEqualTo("A friendly hello.");
        assertThat(vi.explanation().translation()).isEqualTo("Xin chào");
        assertThat(ja.cached()).isTrue();
        assertThat(ja.explanation().meaning()).isEqualTo("A friendly hello.");
        assertThat(ja.explanation().translation()).isEqualTo("こんにちは");
        // One full sentence call for both languages, one small line call per language.
        verify(claude, times(1)).explainSentence(anyString());
        verify(claude, times(1)).translateLines(TargetLanguage.VI, List.of("Hello there"));
        verify(claude, times(1)).translateLines(TargetLanguage.JA, List.of("Hello there"));
        assertThat(sentences.count()).isEqualTo(1);
        assertThat(lines.count()).isEqualTo(2);
    }

    @Test
    void theSameLanguageAgainCostsNothing() {
        when(claude.explainSentence("Hello there")).thenReturn(ENGLISH);
        when(claude.translateLines(any(), anyList())).thenReturn(List.of("Xin chào"));

        service.translate("Hello there", TargetLanguage.VI);
        TranslationResponse again = service.translate("Hello there", TargetLanguage.VI);

        assertThat(again.explanation().translation()).isEqualTo("Xin chào");
        verify(claude, times(1)).explainSentence(anyString());
        verify(claude, times(1)).translateLines(any(), anyList());
    }

    @Test
    void theEnglishPartIsSavedWithoutALanguageUnderTheCurrentKey() {
        when(claude.explainSentence("Hello there")).thenReturn(ENGLISH);
        when(claude.translateLines(any(), anyList())).thenReturn(List.of("Xin chào"));

        service.translate("Hello there", TargetLanguage.VI);

        SentenceTranslation row = sentences.findByCacheKey(CacheKeys.sentenceKey("v11", "Hello there")).orElseThrow();
        assertThat(row.getPromptVersion()).isEqualTo("v11");
        assertThat(row.getTargetLang()).isEqualTo("en");
        assertThat(row.getTranslation()).isEmpty();
        assertThat(row.getMatchKey()).isEqualTo(CacheKeys.sentenceMatchKey("v11", "hello there"));
    }

    @Test
    void theLooseTextFindsTheEnglishPartInAnotherLanguageToo() {
        when(claude.explainSentence("How are you?")).thenReturn(ENGLISH);
        when(claude.translateLines(any(), anyList())).thenReturn(List.of("line"));

        service.translate("How are you?", TargetLanguage.VI);
        TranslationResponse ko = service.translate("how are you", TargetLanguage.KO);

        assertThat(ko.cached()).isTrue();
        verify(claude, times(1)).explainSentence(anyString());
        // The line is for the text the user typed, so it is a new line.
        verify(claude).translateLines(TargetLanguage.KO, List.of("how are you"));
    }

    @Test
    void englishNeedsNoLineCall() {
        when(claude.explainSentence("Hello there")).thenReturn(ENGLISH);

        TranslationResponse response = service.translate("Hello there", TargetLanguage.EN);

        assertThat(response.explanation().translation()).isEqualTo("Hello there");
        verify(claude, never()).translateLines(any(), anyList());
    }

    @Test
    void aFailedLineCallStillGivesTheEnglishPart() {
        when(claude.explainSentence("Hello there")).thenReturn(ENGLISH);
        when(claude.translateLines(any(), anyList())).thenThrow(new IllegalStateException("gateway down"));

        TranslationResponse response = service.translate("Hello there", TargetLanguage.VI);

        assertThat(response.explanation().meaning()).isEqualTo("A friendly hello.");
        assertThat(response.explanation().translation()).isEmpty();
        assertThat(sentences.count()).isEqualTo(1);
    }

    @Test
    void streamingSendsTheMeaningAndTheLineArrivesInTheFinalAnswer() {
        when(claude.explainSentenceStreaming(any(), any())).thenAnswer(call -> {
            call.<java.util.function.Consumer<String>>getArgument(1).accept("{\"isEnglish\":true,\"summary\":\"A friendly hello.\"}");
            return ENGLISH;
        });
        when(claude.translateLines(TargetLanguage.VI, List.of("Hello there"))).thenReturn(List.of("Xin chào"));
        List<String> meanings = new ArrayList<>();

        TranslationResponse response = service.translateStreaming("Hello there", TargetLanguage.VI, meanings::add);

        assertThat(meanings).containsExactly("A friendly hello.");
        assertThat(response.explanation().translation()).isEqualTo("Xin chào");
    }

    @Test
    void aPersonalSentenceGetsALineButItIsNeverSaved() {
        String text = "Email me at john@example.com";
        when(claude.explainSentence(text)).thenReturn(ENGLISH);
        when(claude.translateLines(TargetLanguage.VI, List.of(text))).thenReturn(List.of("Gửi email cho tôi"));

        TranslationResponse response = service.translate(text, TargetLanguage.VI);

        assertThat(response.explanation().translation()).isEqualTo("Gửi email cho tôi");
        assertThat(sentences.count()).isZero();
        assertThat(lines.count()).isZero();
    }

    @Test
    void aSavedLineIsUsedForCachedOnlyAndAMissIsNull() {
        String version = properties.sentencePromptVersion();
        sentences.saveAndFlush(new SentenceTranslation(CacheKeys.sentenceKey(version, "Hello there"), "Hello there",
                "en", "A friendly hello.", "[]", "", properties.model(), version));
        lines.saveAndFlush(new LineTranslation(CacheKeys.key("Hello there"), "Hello there", "vi", "v1", "Xin chào",
                "seed", Instant.now()));

        TranslationResponse vi = service.translate("Hello there", TargetLanguage.VI, true);
        TranslationResponse ko = service.translate("Hello there", TargetLanguage.KO, true);

        assertThat(vi.explanation().translation()).isEqualTo("Xin chào");
        assertThat(ko.explanation().meaning()).isEqualTo("A friendly hello.");
        assertThat(ko.explanation().translation()).isEmpty();
        verify(claude, never()).translateLines(any(), anyList());
        verify(claude, never()).explainSentence(anyString());
    }
}
