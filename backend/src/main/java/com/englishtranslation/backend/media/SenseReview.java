package com.englishtranslation.backend.media;

/**
 * Where one sense stands in the picture review (TASK-80).
 *
 * @param state   "approved" (the admin approved the CURRENT media file), "to_review" (has media, nobody approved this
 *                file), "wrong" (media_status is needs_ai) or "none" (no media)
 * @param verdict the newest media_check verdict for the current media ("good", "wrong", "unsure"), or null
 * @param reason  Claude's one-sentence reason for that verdict, or null
 */
public record SenseReview(String state, String verdict, String reason) {

    public static final String APPROVED = "approved";
    public static final String TO_REVIEW = "to_review";
    public static final String WRONG = "wrong";
    public static final String NONE = "none";

    /** The rule in one place. needs_ai wins; an approval counts only while it names the media the sense has now. */
    public static String stateOf(Long mediaId, String mediaStatus, Long reviewedMediaId) {
        if ("needs_ai".equals(mediaStatus)) {
            return WRONG;
        }
        if (mediaId == null) {
            return NONE;
        }
        return mediaId.equals(reviewedMediaId) ? APPROVED : TO_REVIEW;
    }
}
