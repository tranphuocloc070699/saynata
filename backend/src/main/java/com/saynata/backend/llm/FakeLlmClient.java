package com.saynata.backend.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Free, offline answer for local development. Replaced when app.llm.provider names a real provider. */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmClient implements LlmClient {

    @Override
    public WordDraft explain(String headword) {
        return new WordDraft("noun", null, "(fake answer) A simple explanation of \"" + headword + "\".");
    }
}
