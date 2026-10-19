package com.englishtranslation.backend.liked;

import java.util.List;

/** The body of GET /api/liked, newest first. */
public record LikedListResponse(List<LikedItemView> items) {
}
