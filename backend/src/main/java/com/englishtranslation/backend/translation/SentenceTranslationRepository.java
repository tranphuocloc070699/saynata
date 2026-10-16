package com.englishtranslation.backend.translation;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceTranslationRepository extends JpaRepository<SentenceTranslation, Long> {

    Optional<SentenceTranslation> findByCacheKey(String cacheKey);

    /** The loose match (TASK-83.3). Two exact texts can share one loose key; the oldest row wins. */
    Optional<SentenceTranslation> findFirstByMatchKeyOrderByCreatedAtAscIdAsc(String matchKey);

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
