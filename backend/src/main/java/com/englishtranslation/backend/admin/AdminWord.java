package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.media.SenseMedia;
import com.englishtranslation.backend.word.WordEntry;
import java.time.Instant;
import java.util.List;

/**
 * One word as the admin web app sees it. entry has the same shape as the public GET /api/words/{word}.
 *
 * @param id     "b-12" for a bundled word (table dictionary_word) or "w-45" for a backend word (table word_lookup)
 * @param source "bundled" or "backend"
 * @param ipa    only bundled words have it here (backend words get theirs at request time)
 * @param media  one item per sense of the entry, in the same order; null for a sense without media.
 *               It carries the Pixabay credit (photographer, page). Empty when the entry cannot be read.
 * @param mediaStatus one item per sense, same order: "needs_ai" when the admin marked the sense for an AI image (TASK-52), else null
 * @param reviewReasons why the word needs review: the lines of SimpleWords.problems for the current entry, for example
 *               "sense 1: the definition uses hard words [abstain]". Computed on every read, not stored. Empty when the
 *               word does not need review, or when the entry is simple enough now (for example after an edit).
 */
public record AdminWord(String id, String word, WordEntry entry, String ipa, boolean needsReview, String source,
        String model, String promptVersion, Instant createdAt, List<SenseMedia> media, List<String> mediaStatus,
        List<String> reviewReasons) {
}
