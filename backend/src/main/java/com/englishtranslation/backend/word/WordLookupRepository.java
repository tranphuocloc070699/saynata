package com.englishtranslation.backend.word;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordLookupRepository extends JpaRepository<WordLookup, Long> {

    Optional<WordLookup> findByCacheKey(String cacheKey);

    /** Rows saved before the cefr column existed (see WordCefrBackfill). */
    List<WordLookup> findByCefrIsNull();
}
