package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.lines.LineTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** TASK-106.6 with sign-in required: a guest keeps the cached English part and gets a null line on a line miss. */
@SpringBootTest(properties = { "app.claude.api-key=test-key", "app.auth.require-sign-in=true" })
class SentenceLanguageLineGuestTest {

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
    void aGuestGetsTheEnglishPartAndANullLineOnALineMiss() {
        String version = properties.sentencePromptVersion();
        sentences.saveAndFlush(new SentenceTranslation(CacheKeys.sentenceKey(version, "Hello there"), "Hello there",
                "en", "A friendly hello.", "[]", "", properties.model(), version));

        TranslationResponse response = service.translate("Hello there", TargetLanguage.JA);

        assertThat(response.cached()).isTrue();
        assertThat(response.explanation().meaning()).isEqualTo("A friendly hello.");
        assertThat(response.explanation().translation()).isEmpty();
        verify(claude, never()).translateLines(any(), anyList());
        assertThat(lines.count()).isZero();
    }
}
