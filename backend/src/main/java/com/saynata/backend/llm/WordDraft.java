package com.saynata.backend.llm;

/** What the LLM gives back for one word. Not trusted until WordLookupService has checked it. */
public record WordDraft(String partOfSpeech, String ipa, String simpleExplanation) {
}
