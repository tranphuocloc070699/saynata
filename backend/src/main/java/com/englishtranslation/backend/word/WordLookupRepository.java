package com.englishtranslation.backend.word;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WordLookupRepository extends JpaRepository<WordLookup, Long> {

    Optional<WordLookup> findByCacheKey(String cacheKey);

    /** Rows saved before the cefr column existed (see WordCefrBackfill). */
    List<WordLookup> findByCefrIsNull();

    /** Which of these cache keys are saved. One query, for checking a whole sentence. */
    @Query("select w.cacheKey from WordLookup w where w.cacheKey in :keys")
    List<String> findCacheKeysIn(@Param("keys") Collection<String> keys);
}
