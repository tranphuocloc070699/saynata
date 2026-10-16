package com.englishtranslation.backend.suggest;

import java.util.List;

/** The reply of GET /api/suggest: the prefix as the server read it, and up to 8 suggestions. */
public record SuggestResponse(String query, List<Suggestion> suggestions) {
}
