package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.media.SenseMedia;
import com.englishtranslation.backend.media.SenseReview;
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
 * @param wrongMediaCount    how many senses are marked needs_ai (picture review, TASK-80)
 * @param mediaToReviewCount how many senses have a picture or clip that nobody approved and that is not marked needs_ai
 * @param mediaReview one item per sense, same order: state ("approved", "to_review", "wrong" or "none") and the newest
 *               Claude check of the current media (verdict and reason, read-only; null when it was not checked)
 */
public record AdminWord(String id, String word, WordEntry entry, String ipa, boolean needsReview, String source,
        String model, String promptVersion, Instant createdAt, List<SenseMedia> media, List<String> mediaStatus,
        List<String> reviewReasons, int wrongMediaCount, int mediaToReviewCount, List<SenseReview> mediaReview) {
}
