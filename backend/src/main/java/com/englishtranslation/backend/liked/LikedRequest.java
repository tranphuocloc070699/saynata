package com.englishtranslation.backend.liked;

/** The body of PUT /api/liked. Checked in LikedService, so the answer says what is wrong. */
public record LikedRequest(String kind, String text, String preview) {
}
