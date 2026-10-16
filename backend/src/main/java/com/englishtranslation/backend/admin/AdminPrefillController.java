package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.prefill.PrefillItemRepository;
import com.englishtranslation.backend.prefill.PrefillProperties;
import com.englishtranslation.backend.prefill.PrefillRunner;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for the prefill job (TASK-83.1): start a run, and see the queue and what the runs spent. ADMIN only. */
@RestController
@RequestMapping("/api/admin/prefill")
public class AdminPrefillController {

    /** The most one manual run may be given, so a typo (50 instead of 0.5) cannot spend real money. */
    static final BigDecimal MAX_ALLOWED_USD = new BigDecimal("10");

    /** The answer of POST /run: the run has started on its own thread. */
    public record Started(boolean started, BigDecimal maxUsd, Integer limit) {
    }

    /** Counts of one source, by status (a status with no items is 0). */
    public record SourceCounts(String source, Map<String, Long> byStatus) {
    }

    public record Overview(boolean running, Map<String, Long> byStatus, List<SourceCounts> bySource,
            BigDecimal maxUsdPerRun, BigDecimal spentTodayUsd, List<PrefillRunner.RunSummary> lastRuns) {
    }

    private static final List<String> STATUSES = List.of("pending", "done", "skipped", "failed");

    private final PrefillRunner runner;
    private final PrefillItemRepository items;
    private final PrefillProperties properties;
    private final ClaudeUsageService usage;

    public AdminPrefillController(PrefillRunner runner, PrefillItemRepository items, PrefillProperties properties,
            ClaudeUsageService usage) {
        this.runner = runner;
        this.items = items;
        this.properties = properties;
        this.usage = usage;
    }

    /**
     * Starts one run in the background. {@code maxUsd} is this run's cap (default app.prefill.max-usd-per-run,
     * at most 10), {@code limit} the most items to handle (default: until the queue is empty or the cap is reached).
     * 409 {@code prefill_running} if a run is already going.
     */
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Started run(@RequestParam(required = false) BigDecimal maxUsd, @RequestParam(required = false) Integer limit) {
        if (maxUsd != null && (maxUsd.signum() <= 0 || maxUsd.compareTo(MAX_ALLOWED_USD) > 0)) {
            throw new InvalidAdminRequestException("maxUsd must be more than 0 and at most " + MAX_ALLOWED_USD + ".");
        }
        if (limit != null && limit < 1) {
            throw new InvalidAdminRequestException("limit must be 1 or more.");
        }
        runner.startInBackground("admin", maxUsd, limit);
        return new Started(true, maxUsd == null ? properties.maxUsdPerRun() : maxUsd, limit);
    }

    @GetMapping
    public Overview overview() {
        Map<String, Long> byStatus = emptyStatuses();
        Map<String, Map<String, Long>> bySource = new TreeMap<>();
        for (Object[] row : items.countsBySourceAndStatus()) {
            String source = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            byStatus.merge(status, count, Long::sum);
            bySource.computeIfAbsent(source, key -> emptyStatuses()).merge(status, count, Long::sum);
        }
        List<SourceCounts> sources = bySource.entrySet().stream()
                .map(entry -> new SourceCounts(entry.getKey(), entry.getValue())).toList();
        BigDecimal spentToday = usage.report(1).prefillTodayUsd();
        return new Overview(runner.isRunning(), byStatus, sources, properties.maxUsdPerRun(), spentToday,
                runner.lastRuns());
    }

    private static Map<String, Long> emptyStatuses() {
        Map<String, Long> map = new LinkedHashMap<>();
        STATUSES.forEach(status -> map.put(status, 0L));
        return map;
    }
}
