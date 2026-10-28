package com.saynata.backend.llm;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, Long> {

    @Query("select coalesce(sum(u.costUsd), 0) from LlmUsage u where u.createdAt >= :since")
    BigDecimal spentSince(@Param("since") Instant since);

    long countByIpAndCreatedAtAfter(String ip, Instant since);
}
