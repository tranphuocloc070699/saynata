package com.englishtranslation.backend.lines;

import com.englishtranslation.backend.translation.TargetLanguage;
import java.util.List;

/**
 * The body of POST /api/translate-lines. An unknown targetLang code fails JSON parsing (400). The rest is checked
 * in {@link LineTranslationService}, so the answer says what is wrong.
 */
public record TranslateLinesRequest(TargetLanguage targetLang, List<String> texts) {
}
