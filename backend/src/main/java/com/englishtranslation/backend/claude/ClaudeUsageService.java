package com.englishtranslation.backend.claude;

import com.englishtranslation.backend.limits.CallerContext;
import com.englishtranslation.backend.limits.PrefillContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Records what each Claude call cost, and tells whether today's budget (UTC day) is used up. */
@Service
public class ClaudeUsageService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeUsageService.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int MAX_REPORT_DAYS = 90;

    /** Totals for one day or one feature. */
    public record Totals(long calls, long inputTokens, long outputTokens, BigDecimal costUsd) {
        static Totals empty() {
            return new Totals(0, 0, 0, BigDecimal.ZERO);
        }

        Totals plus(ClaudeUsage row) {
            return new Totals(calls + 1, inputTokens + row.getInputTokens(), outputTokens + row.getOutputTokens(),
                    costUsd.add(row.getCostUsd()));
        }
    }

    /** One day of spend, split by feature (keys are lower case: sentence, word, admin). */
    public record Day(LocalDate date, Totals total, Map<String, Totals> byFeature) {
    }

    /**
     * @param todayUsd        today's spend that counts toward the users' budget (prefill not included)
     * @param prefillTodayUsd today's spend of the prefill job (it has its own cap)
     */
    public record Report(BigDecimal dailyBudgetUsd, BigDecimal todayUsd, BigDecimal prefillTodayUsd, List<Day> days) {
    }

    private final ClaudeUsageRepository repository;
    private final UsageProperties properties;
    private final Clock clock;

    public ClaudeUsageService(ClaudeUsageRepository repository, UsageProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Saves the usage of one call. Never throws: a broken usage table must not take the answer away from a
     * learner who already waited for Claude.
     */
    public void record(Feature feature, String model, long inputTokens, long outputTokens) {
        // A call made by the prefill job is always saved as PREFILL, whatever part of the code made it.
        if (PrefillContext.current().isPresent() && feature != Feature.WARMUP) {
            feature = Feature.PREFILL;
        }
        try {
            BigDecimal cost = cost(model, inputTokens, outputTokens);
            // The night job has no user; a person's call keeps their id for the admin Users page (TASK-105).
            Long userId = feature == Feature.PREFILL ? null : CallerContext.userId().orElse(null);
            repository.save(new ClaudeUsage(clock.instant(), model, feature, inputTokens, outputTokens, cost, userId));
        } catch (RuntimeException e) {
            log.error("Could not save the Claude usage ({} {} in={} out={}): {}", feature, model, inputTokens,
                    outputTokens, e.toString());
        }
    }

    /**
     * US dollars for one call: tokens times the price per million tokens, for input and output.
     * A model without a price is counted at the highest configured price, so a typo never makes calls free.
     */
    BigDecimal cost(String model, long inputTokens, long outputTokens) {
        return cost(priceOf(model), inputTokens, outputTokens);
    }

    static BigDecimal cost(UsageProperties.Price price, long inputTokens, long outputTokens) {
        BigDecimal input = price.inputPerMtok().multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal output = price.outputPerMtok().multiply(BigDecimal.valueOf(outputTokens));
        return input.add(output).divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    private UsageProperties.Price priceOf(String model) {
        Map<String, UsageProperties.Price> prices = properties.pricing() == null ? Map.of() : properties.pricing();
        UsageProperties.Price price = prices.get(model);
        if (price != null) {
            return price;
        }
        log.warn("No price for model '{}' in app.claude.pricing. Using the highest configured price.", model);
        return prices.values().stream()
                .max(Comparator.comparing(UsageProperties.Price::outputPerMtok))
                .orElse(new UsageProperties.Price(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /** Start of today in UTC. The budget resets at this moment. */
    public Instant startOfToday() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public BigDecimal spentToday() {
        return repository.totalCostSince(startOfToday());
    }

    /** What the admin tools spent today (UTC day): the AI picture wand has its own daily cap on this (TASK-91). */
    public BigDecimal adminSpentToday() {
        return repository.adminCostSince(startOfToday());
    }

    /** What the prefill job spent since {@code from} (the start of its run). */
    public BigDecimal prefillSpentSince(Instant from) {
        return repository.prefillCostSince(from);
    }

    public boolean budgetReached() {
        return spentToday().compareTo(properties.dailyBudgetUsd()) >= 0;
    }

    /** Spend of the last {@code days} UTC days (today included), newest first. Days without calls are zero. */
    public Report report(int days) {
        int count = Math.max(1, Math.min(days, MAX_REPORT_DAYS));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate first = today.minusDays(count - 1L);
        List<ClaudeUsage> rows = repository.findByCreatedAtGreaterThanEqual(first.atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<LocalDate, Totals> totals = new TreeMap<>(Comparator.reverseOrder());
        Map<LocalDate, Map<Feature, Totals>> features = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 0; i < count; i++) {
            LocalDate date = today.minusDays(i);
            totals.put(date, Totals.empty());
            features.put(date, new EnumMap<>(Feature.class));
        }
        for (ClaudeUsage row : rows) {
            LocalDate date = row.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (!totals.containsKey(date)) {
                continue;
            }
            totals.merge(date, Totals.empty().plus(row), (a, b) -> new Totals(a.calls() + b.calls(),
                    a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens(), a.costUsd().add(b.costUsd())));
            features.get(date).merge(row.getFeature(), Totals.empty().plus(row), (a, b) -> new Totals(
                    a.calls() + b.calls(), a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens(),
                    a.costUsd().add(b.costUsd())));
        }
        List<Day> result = new ArrayList<>();
        totals.forEach((date, total) -> {
            Map<String, Totals> byFeature = new TreeMap<>();
            features.get(date).forEach((feature, value) -> byFeature.put(feature.name().toLowerCase(), value));
            result.add(new Day(date, total, byFeature));
        });
        Totals todayPrefill = result.get(0).byFeature().getOrDefault("prefill", Totals.empty());
        BigDecimal todayUsd = result.get(0).total().costUsd().subtract(todayPrefill.costUsd());
        return new Report(properties.dailyBudgetUsd(), todayUsd, todayPrefill.costUsd(), result);
    }
}
