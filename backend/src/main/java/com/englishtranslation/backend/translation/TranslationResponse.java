package com.englishtranslation.backend.translation;

public record TranslationResponse(String text, TargetLanguage targetLang, Explanation explanation, boolean cached) {
}
