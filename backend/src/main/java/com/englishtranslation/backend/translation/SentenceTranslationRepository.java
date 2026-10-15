package com.englishtranslation.backend.translation;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceTranslationRepository extends JpaRepository<SentenceTranslation, Long> {

    Optional<SentenceTranslation> findByCacheKey(String cacheKey);

    /** Admin list. likePattern is like "%hello%" ("%" alone matches every sentence). */
    @Query("""
            select s from SentenceTranslation s
            where lower(s.sourceText) like :likePattern escape '\\'
              and (:filterReview = false or s.needsReview = :needsReview)
            """)
    Page<SentenceTranslation> search(@Param("likePattern") String likePattern,
            @Param("filterReview") boolean filterReview, @Param("needsReview") boolean needsReview,
            Pageable pageable);
}
