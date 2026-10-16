package com.englishtranslation.backend.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheKeysTest {

    @Test
    void sentenceKeepsCaseButCollapsesWhitespace() {
        assertThat(CacheKeys.normalizeSentence("  I   love\n US  ")).isEqualTo("I love US");
    }

    @Test
    void wordIsLowercased() {
        assertThat(CacheKeys.normalizeWord(" Reluctant ")).isEqualTo("reluctant");
    }

    @Test
    void looseSentenceIgnoresCaseAndFinalPunctuation() {
        assertThat(CacheKeys.normalizeSentenceLoose("  How   are YOU?! ")).isEqualTo("how are you");
        assertThat(CacheKeys.normalizeSentenceLoose("Wait... what?")).isEqualTo("wait... what");
        assertThat(CacheKeys.normalizeSentenceLoose("Hello . ")).isEqualTo("hello");
        assertThat(CacheKeys.normalizeSentenceLoose("?!")).isEmpty();
    }

    @Test
    void matchKeyIsTheDocumentedSha256() {
        assertThat(CacheKeys.sentenceMatchKey("v6", "vi", "How are you?"))
                .isEqualTo(CacheKeys.key("sentence-loose", "v6", "vi", "how are you"))
                .isEqualTo(CacheKeys.sentenceMatchKey("v6", "vi", "how are you"))
                .hasSize(64);
        assertThat(CacheKeys.sentenceMatchKey("v6", "vi", "how are you"))
                .isNotEqualTo(CacheKeys.sentenceMatchKey("v6", "ja", "how are you"))
                .isNotEqualTo(CacheKeys.sentenceMatchKey("v7", "vi", "how are you"))
                .isNotEqualTo(CacheKeys.key("sentence", "v6", "vi", "how are you"));
    }

    @Test
    void keyChangesWithPromptVersionAndKind() {
        String v1 = CacheKeys.key("sentence", "v1", "hello");
        assertThat(v1).hasSize(64).isEqualTo(CacheKeys.key("sentence", "v1", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("sentence", "v2", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("word", "v1", "hello"));
    }
}
