package com.englishtranslation.backend.translation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An unknown targetLang code fails JSON parsing, so Spring answers 400 before this is built. */
public record TranslationRequest(
        @NotBlank @Size(max = 500) String text,
        @NotNull TargetLanguage targetLang) {
}
