package com.englishtranslation.backend.liked;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.common.ApiException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Liked words and sentences of one user. No Claude call here, so ClaudeGate is not involved. */
@Service
public class LikedService {

    static final int MAX_TEXT = 500;
    static final int MAX_PREVIEW = 300;
    static final int MAX_ITEMS = 5000;

    private final LikedItemRepository items;
    private final Clock clock;

    public LikedService(LikedItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    public List<LikedItemView> list(long userId) {
        return items.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(LikedItemView::of).toList();
    }

    /** Likes an item. Liking the same item again keeps one row and only updates its text and preview. */
    public LikedItemView like(long userId, LikedRequest request) {
        String kind = checkKind(request.kind());
        String text = request.text() == null ? "" : request.text().strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw bad("invalid_text", "Text must be 1-" + MAX_TEXT + " characters.");
        }
        String preview = request.preview() == null ? "" : request.preview().strip();
        if (preview.length() > MAX_PREVIEW) {
            throw bad("invalid_preview", "Preview must be at most " + MAX_PREVIEW + " characters.");
        }
        String textKey = key(kind, text);

        LikedItem existing = items.findByUserIdAndKindAndTextKey(userId, kind, textKey).orElse(null);
        if (existing != null) {
            existing.update(text, preview);
            return LikedItemView.of(items.save(existing));
        }
        if (items.countByUserId(userId) >= MAX_ITEMS) {
            throw bad("liked_limit_reached", "You can like at most " + MAX_ITEMS + " items. Remove some first.");
        }
        try {
            return LikedItemView.of(items.saveAndFlush(new LikedItem(userId, kind, textKey, text, preview, clock.instant())));
        } catch (DataIntegrityViolationException e) {
            // Two requests liked the same item at the same moment: the other one saved it first.
            LikedItem saved = items.findByUserIdAndKindAndTextKey(userId, kind, textKey).orElseThrow(() -> e);
            saved.update(text, preview);
            return LikedItemView.of(items.save(saved));
        }
    }

    /** Removes an item. Nothing there is not an error. */
    public void unlike(long userId, String kind, String text) {
        String checkedKind = checkKind(kind);
        items.deleteItem(userId, checkedKind, key(checkedKind, text == null ? "" : text));
    }

    private static String checkKind(String kind) {
        if ("word".equals(kind) || "sentence".equals(kind)) {
            return kind;
        }
        throw bad("invalid_kind", "Kind must be \"word\" or \"sentence\".");
    }

    /** Word: same as the word lookup (trim, one space, lowercase). Sentence: trimmed, case kept. */
    private static String key(String kind, String text) {
        return kind.equals("word") ? CacheKeys.normalizeWord(text) : text.strip();
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
