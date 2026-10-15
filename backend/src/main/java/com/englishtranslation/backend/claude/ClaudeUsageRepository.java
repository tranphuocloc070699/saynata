package com.englishtranslation.backend.claude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaudeUsageRepository extends JpaRepository<ClaudeUsage, Long> {

    @Query("select coalesce(sum(u.costUsd), 0) from ClaudeUsage u where u.createdAt >= :from")
    BigDecimal totalCostSince(@Param("from") Instant from);

    List<ClaudeUsage> findByCreatedAtGreaterThanEqual(Instant from);
}
