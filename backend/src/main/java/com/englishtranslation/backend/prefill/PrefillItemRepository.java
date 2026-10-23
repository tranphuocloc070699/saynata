package com.englishtranslation.backend.prefill;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrefillItemRepository extends JpaRepository<PrefillItem, Long> {

    /**
     * The next items to answer, best priority first. Pending ones, and failed ones that have tries left and were
     * not tried in the current run ({@code before} = the start of the run), so one bad item cannot loop in a run.
     * Pass {@code PageRequest.of(0, 1)} to get just the next one.
     * {@code allSources} true = every source; false = only the sources in {@code sources} (TASK-83.12; the list must
     * not be empty, because an empty IN list is not valid SQL on every database).
     */
    @Query("""
            select p from PrefillItem p
            where (p.status = 'pending'
               or (p.status = 'failed' and p.attempts < :maxAttempts and p.updatedAt < :before))
              and (:allSources = true or p.source in :sources)
            order by p.priority desc, p.id asc
            """)
    List<PrefillItem> next(@Param("maxAttempts") int maxAttempts, @Param("before") Instant before,
            @Param("allSources") boolean allSources, @Param("sources") Collection<String> sources, Pageable page);

    /** Rows of (source, status, count) for the admin overview. */
    @Query("select p.source, p.status, count(p) from PrefillItem p group by p.source, p.status")
    List<Object[]> countsBySourceAndStatus();

    /**
     * Admin list (TASK-100). Empty kind or status means any. likePattern is like "%lan%" ("%" alone matches every row).
     */
    @Query("""
            select p from PrefillItem p
            where (:kind = '' or p.kind = :kind)
              and (:status = '' or p.status = :status)
              and lower(p.text) like :likePattern escape '\\'
            """)
    Page<PrefillItem> search(@Param("kind") String kind, @Param("status") String status,
            @Param("likePattern") String likePattern, Pageable page);

    Optional<PrefillItem> findByKindAndTextAndTargetLang(String kind, String text, String targetLang);
}
