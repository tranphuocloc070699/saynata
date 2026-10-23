package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.prefill.PrefillItem;
import com.englishtranslation.backend.prefill.PrefillItemRepository;
import com.englishtranslation.backend.prefill.PrefillProperties;
import com.englishtranslation.backend.prefill.PrefillRunner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin API for the prefill job (TASK-83.1): start a run, and see the queue and what the runs spent; list the queue
 * rows and take one out (TASK-100). ADMIN only.
 */
@RestController
@RequestMapping("/api/admin/prefill")
public class AdminPrefillController {

    /** The most one manual run may be given, so a typo (50 instead of 0.5) cannot spend real money. */
    static final BigDecimal MAX_ALLOWED_USD = new BigDecimal("10");

    /** The answer of POST /run: the run has started on its own thread. */
    public record Started(boolean started, BigDecimal maxUsd, Integer limit, List<String> sources) {
    }

    /** Counts of one source, by status (a status with no items is 0). */
    public record SourceCounts(String source, Map<String, Long> byStatus) {
    }

    public record Overview(boolean running, Map<String, Long> byStatus, List<SourceCounts> bySource,
            BigDecimal maxUsdPerRun, BigDecimal spentTodayUsd, List<PrefillRunner.RunSummary> lastRuns) {
    }

    /** One queue row as the admin web app sees it (TASK-100). */
    public record AdminPrefillItem(Long id, String kind, String text, String targetLang, String source, int seenCount,
            int priority, String status, int attempts, String lastError, Instant createdAt, Instant updatedAt) {
    }

    private static final List<String> STATUSES = List.of("pending", "done", "skipped", "failed");
    private static final List<String> KINDS = List.of("word", "sentence");

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "text", "text",
            "seenCount", "seenCount",
            "priority", "priority",
            "createdAt", "createdAt");

    private final PrefillRunner runner;
    private final PrefillItemRepository items;
    private final PrefillProperties properties;
    private final ClaudeUsageService usage;
    private final Clock clock;

    public AdminPrefillController(PrefillRunner runner, PrefillItemRepository items, PrefillProperties properties,
            ClaudeUsageService usage, Clock clock) {
        this.runner = runner;
        this.items = items;
        this.properties = properties;
        this.usage = usage;
        this.clock = clock;
    }

    /**
     * Starts one run in the background. {@code maxUsd} is this run's cap (default app.prefill.max-usd-per-run,
     * at most 10), {@code limit} the most items to handle (default: until the queue is empty or the cap is reached).
     * {@code source} (repeatable, TASK-83.12) limits the run to those queue sources; without it, every source.
     * 409 {@code prefill_running} if a run is already going.
     */
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Started run(@RequestParam(required = false) BigDecimal maxUsd, @RequestParam(required = false) Integer limit,
            @RequestParam(name = "source", required = false) List<String> source) {
        if (maxUsd != null && (maxUsd.signum() <= 0 || maxUsd.compareTo(MAX_ALLOWED_USD) > 0)) {
            throw new InvalidAdminRequestException("maxUsd must be more than 0 and at most " + MAX_ALLOWED_USD + ".");
        }
        if (limit != null && limit < 1) {
            throw new InvalidAdminRequestException("limit must be 1 or more.");
        }
        List<String> sources = source == null ? List.of()
                : source.stream().map(String::strip).filter(name -> !name.isEmpty()).distinct().toList();
        runner.startInBackground("admin", maxUsd, limit, sources);
        return new Started(true, maxUsd == null ? properties.maxUsdPerRun() : maxUsd, limit, sources);
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

    /**
     * The queue rows, newest first by default (TASK-100). {@code kind} word or sentence, {@code status} pending, done,
     * skipped or failed; both optional. Same paging as the word list: {@code _start}, {@code _end}, X-Total-Count.
     */
    @GetMapping("/items")
    public ResponseEntity<List<AdminPrefillItem>> items(
            @RequestParam(name = "_start", defaultValue = "0") int start,
            @RequestParam(name = "_end", defaultValue = "10") int end,
            @RequestParam(name = "_sort", defaultValue = "id") String sort,
            @RequestParam(name = "_order", defaultValue = "DESC") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "kind", required = false) String kind,
            @RequestParam(name = "status", required = false) String status) {
        if (kind != null && !kind.isEmpty() && !KINDS.contains(kind)) {
            throw new InvalidAdminRequestException("kind must be word or sentence.");
        }
        if (status != null && !status.isEmpty() && !STATUSES.contains(status)) {
            throw new InvalidAdminRequestException("status must be one of " + STATUSES + ".");
        }
        Page<PrefillItem> page = items.search(kind == null ? "" : kind, status == null ? "" : status,
                AdminListParams.likePattern(q), AdminListParams.pageable(start, end, sort, order, SORT_FIELDS));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(page.getContent().stream().map(AdminPrefillController::toAdmin).toList());
    }

    /**
     * Takes a row out of the queue. The row is kept with status skipped, so the same word is never queued again
     * when a learner meets it in another sentence (SentenceWordQueue leaves done and skipped rows alone).
     */
    @DeleteMapping("/items/{id}")
    public AdminPrefillItem remove(@PathVariable long id) {
        PrefillItem item = items.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No queue item with id " + id + "."));
        item.finish(PrefillItem.SKIPPED, "Removed by the admin", clock.instant());
        return toAdmin(items.save(item));
    }

    private static AdminPrefillItem toAdmin(PrefillItem item) {
        return new AdminPrefillItem(item.getId(), item.getKind(), item.getText(), item.getTargetLang(),
                item.getSource(), item.getSeenCount(), item.getPriority(), item.getStatus(), item.getAttempts(),
                item.getLastError(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private static Map<String, Long> emptyStatuses() {
        Map<String, Long> map = new LinkedHashMap<>();
        STATUSES.forEach(status -> map.put(status, 0L));
        return map;
    }
}
