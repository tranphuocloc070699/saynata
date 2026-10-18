package com.englishtranslation.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The id the admin API uses for a word: "b-12" is dictionary_word 12 (bundled), "w-45" is word_lookup 45 (backend).
 * The two tables count from 1 each, so the letter keeps the ids from colliding.
 */
public record AdminWordId(boolean bundled, long number) {

    static final String BUNDLED = "bundled";
    static final String BACKEND = "backend";

    /** An id that is not "b-<number>" or "w-<number>" is a 404, like any other id we do not have. */
    public static AdminWordId parse(String id) {
        if (id != null && id.length() > 2 && id.length() < 21 && id.charAt(1) == '-'
                && (id.charAt(0) == 'b' || id.charAt(0) == 'w')
                && id.substring(2).chars().allMatch(Character::isDigit)) {
            return new AdminWordId(id.charAt(0) == 'b', Long.parseLong(id.substring(2)));
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No word with id " + id + ".");
    }

    public String text() {
        return (bundled ? "b-" : "w-") + number;
    }

    String source() {
        return bundled ? BUNDLED : BACKEND;
    }
}
