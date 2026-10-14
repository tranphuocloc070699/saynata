package com.englishtranslation.backend.admin;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns Refine's list parameters (_start, _end, _sort, _order, q) into Spring Data objects.
 * Shared by the words and the sentences controllers.
 */
final class AdminListParams {

    private static final int MAX_PAGE_SIZE = 200;

    private AdminListParams() {
    }

    /**
     * Refine sends _start = (page - 1) * pageSize and _end = _start + pageSize.
     *
     * @param sortFields maps the name Refine sends (for example "createdAt") to the entity field
     */
    static Pageable pageable(int start, int end, String sort, String order, Map<String, String> sortFields) {
        int size = end - start;
        if (start < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidAdminRequestException(
                    "_start must be 0 or more and _end - _start must be 1-" + MAX_PAGE_SIZE + ".");
        }
        if (start % size != 0) {
            throw new InvalidAdminRequestException("_start must be a multiple of the page size (_end - _start).");
        }
        String field = sortFields.get(sort);
        if (field == null) {
            throw new InvalidAdminRequestException(
                    "_sort must be one of: " + String.join(", ", sortFields.keySet()) + ".");
        }
        Sort.Direction direction = "ASC".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(start / size, size, Sort.by(direction, field));
    }

    /** "run" becomes "%run%" in lower case. "%", "_" and "\" in the text are escaped, so they match themselves. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
