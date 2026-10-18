package com.englishtranslation.backend.claude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaudeUsageRepository extends JpaRepository<ClaudeUsage, Long> {

    /** What the users' budget counts: every feature except PREFILL, which has its own cap. */
    @Query("select coalesce(sum(u.costUsd), 0) from ClaudeUsage u where u.createdAt >= :from"
            + " and u.feature <> com.englishtranslation.backend.claude.Feature.PREFILL")
    BigDecimal totalCostSince(@Param("from") Instant from);

    @Query("select coalesce(sum(u.costUsd), 0) from ClaudeUsage u where u.createdAt >= :from"
            + " and u.feature = com.englishtranslation.backend.claude.Feature.PREFILL")
    BigDecimal prefillCostSince(@Param("from") Instant from);

    /** What the admin tools spent (the AI picture wand, TASK-91). Counted in totalCostSince too. */
    @Query("select coalesce(sum(u.costUsd), 0) from ClaudeUsage u where u.createdAt >= :from"
            + " and u.feature = com.englishtranslation.backend.claude.Feature.ADMIN")
    BigDecimal adminCostSince(@Param("from") Instant from);

    List<ClaudeUsage> findByCreatedAtGreaterThanEqual(Instant from);
}
