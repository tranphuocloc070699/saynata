package com.englishtranslation.backend.prefill;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrefillItemRepository extends JpaRepository<PrefillItem, Long> {

    /**
     * The next items to answer, best priority first. Pending ones, and failed ones that have tries left and were
     * not tried in the current run ({@code before} = the start of the run), so one bad item cannot loop in a run.
     * Pass {@code PageRequest.of(0, 1)} to get just the next one.
     */
    @Query("""
            select p from PrefillItem p
            where p.status = 'pending'
               or (p.status = 'failed' and p.attempts < :maxAttempts and p.updatedAt < :before)
            order by p.priority desc, p.id asc
            """)
    List<PrefillItem> next(@Param("maxAttempts") int maxAttempts, @Param("before") Instant before, Pageable page);

    /** Rows of (source, status, count) for the admin overview. */
    @Query("select p.source, p.status, count(p) from PrefillItem p group by p.source, p.status")
    List<Object[]> countsBySourceAndStatus();
}
