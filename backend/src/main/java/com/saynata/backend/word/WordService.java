package com.saynata.backend.word;

import com.saynata.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules live here. The controller only handles HTTP.
 * Errors are ApiException: a status, a stable code for the apps, and a message for humans.
 */
@Service
public class WordService {

    private final WordRepository words;

    // Constructor injection: the dependency is visible, final, and easy to fake in tests.
    public WordService(WordRepository words) {
        this.words = words;
    }

    @Transactional
    public WordResponse create(WordRequest request) {
        String headword = request.headword().trim();
        // Friendly check. The unique index in the database is still the real guard
        // (two requests at the same moment can both pass this check; ApiExceptionHandler maps that to 409).
        if (words.existsByHeadwordIgnoreCase(headword)) {
            throw new ApiException(HttpStatus.CONFLICT, "word_already_exists", "Word already exists: " + headword);
        }
        Word saved = words.save(new Word(
                headword,
                request.partOfSpeech(),
                request.ipa(),
                request.simpleExplanation().trim()));
        return WordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public WordResponse get(Long id) {
        return words.findById(id)
                .map(WordResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "word_not_found", "Word not found: " + id));
    }

    @Transactional(readOnly = true)
    public WordResponse findByHeadword(String headword) {
        return words.findByHeadwordIgnoreCase(headword.trim())
                .map(WordResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "word_not_found", "Word not found: " + headword));
    }
}
