package com.englishtranslation.backend.word;

public record WordResponse(String word, WordEntry entry, boolean cached) {
}
