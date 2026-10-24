package com.englishtranslation.backend.lines;

import com.englishtranslation.backend.translation.TargetLanguage;
import java.util.List;

/** The answer: one line per text, in the order of the request. */
public record TranslateLinesResponse(TargetLanguage targetLang, List<Line> lines) {

    /** {@code translation} is null for a miss on a cachedOnly request. */
    public record Line(String text, String translation, boolean cached) {
    }
}
