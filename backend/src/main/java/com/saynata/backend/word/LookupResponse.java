package com.saynata.backend.word;

/** source is "cache" (already in our database) or "llm" (made just now). */
public record LookupResponse(WordResponse word, String source) {
}
