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
    void looseSentenceMakesQuotesStraightAndDropsEllipsisAndCommas() {
        assertThat(CacheKeys.normalizeSentenceLoose("I\u2019m fine\u2026")).isEqualTo("i'm fine");
        assertThat(CacheKeys.normalizeSentenceLoose("He said \u201Chi\u201D, then left,"))
                .isEqualTo("he said \"hi\", then left");
        assertThat(CacheKeys.normalizeSentenceLoose("Looks\u00A0 good!")).isEqualTo("looks good");
        // Inner punctuation stays: only the end is loose.
        assertThat(CacheKeys.normalizeSentenceLoose("Well, okay.")).isEqualTo("well, okay");
        // The decision (doc-7): a final "?" is ignored like "." and "!".
        assertThat(CacheKeys.normalizeSentenceLoose("Is it OK?")).isEqualTo(CacheKeys.normalizeSentenceLoose("is it ok"));
    }

    @Test
    void looseSentenceDropsOnePairOfQuotesAroundTheWholeText() {
        String plain = CacheKeys.normalizeSentenceLoose("Looks good.");
        assertThat(plain).isEqualTo("looks good");
        assertThat(CacheKeys.normalizeSentenceLoose("\"Looks good.\"")).isEqualTo(plain);
        assertThat(CacheKeys.normalizeSentenceLoose("\u201CLooks good.\u201D")).isEqualTo(plain);
        assertThat(CacheKeys.normalizeSentenceLoose("'Looks good.'")).isEqualTo(plain);
        assertThat(CacheKeys.normalizeSentenceLoose("\u2018Looks good.\u2019")).isEqualTo(plain);
        assertThat(CacheKeys.normalizeSentenceLoose("\"Looks good\".")).isEqualTo(plain);
        assertThat(CacheKeys.normalizeSentenceLoose("  \"Looks good\"  ")).isEqualTo(plain);
        // Only one pair goes.
        assertThat(CacheKeys.normalizeSentenceLoose("\"'Looks good'\"")).isEqualTo("'looks good'");
    }

    @Test
    void looseSentenceKeepsInnerQuotesAndApostropheWords() {
        assertThat(CacheKeys.normalizeSentenceLoose("She said \"hi\" to me")).isEqualTo("she said \"hi\" to me");
        assertThat(CacheKeys.normalizeSentenceLoose("'Cause I said so.")).isEqualTo("'cause i said so");
        assertThat(CacheKeys.normalizeSentenceLoose("\"Hi\" and \"bye\"")).isEqualTo("\"hi\" and \"bye\"");
        assertThat(CacheKeys.normalizeSentenceLoose("'a' and 'b'")).isEqualTo("'a' and 'b'");
        assertThat(CacheKeys.normalizeSentenceLoose("'Don't go.'")).isEqualTo("don't go");
        assertThat(CacheKeys.normalizeSentenceLoose("\"")).isEqualTo("\"");
        assertThat(CacheKeys.normalizeSentenceLoose("\"\"")).isEmpty();
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
    void sentenceKeysHaveNoLanguageSinceV10() {
        assertThat(CacheKeys.sentenceKey("v10", "How are you?"))
                .isEqualTo(CacheKeys.key("sentence", "v10", "How are you?"))
                .isNotEqualTo(CacheKeys.sentenceKey("v9", "How are you?"))
                .hasSize(64);
        assertThat(CacheKeys.sentenceMatchKey("v10", "How are you?"))
                .isEqualTo(CacheKeys.key("sentence-loose", "v10", "how are you"))
                .isEqualTo(CacheKeys.sentenceMatchKey("v10", "how are you"));
    }

    @Test
    void keyChangesWithPromptVersionAndKind() {
        String v1 = CacheKeys.key("sentence", "v1", "hello");
        assertThat(v1).hasSize(64).isEqualTo(CacheKeys.key("sentence", "v1", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("sentence", "v2", "hello"));
        assertThat(v1).isNotEqualTo(CacheKeys.key("word", "v1", "hello"));
    }
}
