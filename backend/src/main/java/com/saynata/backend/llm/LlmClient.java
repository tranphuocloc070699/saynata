package com.saynata.backend.llm;

/**
 * The only door to the LLM. The rest of the app depends on this interface, so tests and local
 * development use a fake, and the real provider can change without touching the lookup code.
 * An implementation throws a RuntimeException when the provider fails.
 */
public interface LlmClient {

    WordDraft explain(String headword);
}
