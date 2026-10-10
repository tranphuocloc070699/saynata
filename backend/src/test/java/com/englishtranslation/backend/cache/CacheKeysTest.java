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
    void keyChangesWithPromptVersionAndKind() {
        String v1 = CacheKeys.key("sentence", "v1", "hello");
        assertThat(v1).hasSize(64).isEqualTo(CacheKeys.key("sentence", "v1", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("sentence", "v2", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("word", "v1", "hello"));
    }
}
