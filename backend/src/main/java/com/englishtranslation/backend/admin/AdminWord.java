package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.word.WordEntry;
import java.time.Instant;

/**
 * One word as the admin web app sees it. entry has the same shape as the public GET /api/words/{word}.
 *
 * @param id     "b-12" for a bundled word (table dictionary_word) or "w-45" for a backend word (table word_lookup)
 * @param source "bundled" or "backend"
 * @param ipa    only bundled words have it here (backend words get theirs at request time)
 */
public record AdminWord(String id, String word, WordEntry entry, String ipa, boolean needsReview, String source,
        String model, String promptVersion, Instant createdAt) {
}
