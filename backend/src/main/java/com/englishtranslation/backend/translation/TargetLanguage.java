package com.englishtranslation.backend.translation;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Languages a sentence can be translated into. JSON uses the code: {"targetLang": "ja"}.
 * To add a language, add a line here. Nothing else changes: the code is already part of the cache key.
 */
public enum TargetLanguage {
    VI("vi", "Vietnamese"),
    EN("en", "English"),
    JA("ja", "Japanese"),
    KO("ko", "Korean"),
    ZH("zh", "Simplified Chinese"),
    FR("fr", "French"),
    ES("es", "Spanish"),
    DE("de", "German");

    private final String code;
    private final String englishName;

    TargetLanguage(String code, String englishName) {
        this.code = code;
        this.englishName = englishName;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** The name Claude sees in the prompt, e.g. "Japanese". */
    public String englishName() {
        return englishName;
    }
}
