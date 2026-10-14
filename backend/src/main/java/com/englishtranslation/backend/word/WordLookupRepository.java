package com.englishtranslation.backend.word;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WordLookupRepository extends JpaRepository<WordLookup, Long> {

    Optional<WordLookup> findByCacheKey(String cacheKey);

    /**
     * Admin list. likePattern is like "%run%" ("%" alone matches every word).
     * When filterReview is false, needsReview is ignored.
     */
    @Query("""
            select w from WordLookup w
            where lower(w.word) like :likePattern escape '\\'
              and (:filterReview = false or w.needsReview = :needsReview)
            """)
    Page<WordLookup> search(@Param("likePattern") String likePattern, @Param("filterReview") boolean filterReview,
            @Param("needsReview") boolean needsReview, Pageable pageable);
}
