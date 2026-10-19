package com.englishtranslation.backend.liked;

import java.time.Instant;

/** One liked item as the app sees it. */
public record LikedItemView(String kind, String text, String preview, Instant createdAt) {

    static LikedItemView of(LikedItem item) {
        return new LikedItemView(item.getKind(), item.getText(), item.getPreview(), item.getCreatedAt());
    }
}
