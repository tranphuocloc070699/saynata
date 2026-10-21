package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The money maths, the UTC day, and the report. Prices here are the test's own, not the real config. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
class ClaudeUsageServiceTest {

    static final UsageProperties.Price HAIKU = new UsageProperties.Price(new BigDecimal("0.10"), new BigDecimal("0.50"));
    static final Instant NOON = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired
    ClaudeUsageRepository repository;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    private ClaudeUsageService serviceAt(Instant now, String budget) {
        UsageProperties properties = new UsageProperties(new BigDecimal(budget), Map.of("claude-haiku-5-5", HAIKU,
                "pricey", new UsageProperties.Price(new BigDecimal("5"), new BigDecimal("25"))));
        return new ClaudeUsageService(repository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void costIsTokensTimesPricePerMillion() {
        // 1,000 in * $0.10/M = $0.0001 ; 500 out * $0.50/M = $0.00025
        assertThat(ClaudeUsageService.cost(HAIKU, 1_000, 500)).isEqualByComparingTo("0.00035");
        // A whole million of each: $0.10 + $0.50
        assertThat(ClaudeUsageService.cost(HAIKU, 1_000_000, 1_000_000)).isEqualByComparingTo("0.60");
        assertThat(ClaudeUsageService.cost(HAIKU, 0, 0)).isEqualByComparingTo("0");
    }

    @Test
    void aModelIdWithADotKeepsItsPriceFromTheRealConfig(@Autowired UsageProperties realProperties) {
        // "deepseek-v4.1-flash" needs [brackets] in application.properties; without them Spring splits the id at the dot.
        UsageProperties.Price price = realProperties.pricing().get("deepseek-v4.1-flash");

        assertThat(price).isNotNull();
        assertThat(price.inputPerMtok()).isEqualByComparingTo("0.30");
        assertThat(price.outputPerMtok()).isEqualByComparingTo("1.20");
    }

    @Test
    void aModelWithoutAPriceIsCountedAtTheHighestPriceNotForFree() {
        ClaudeUsageService service = serviceAt(NOON, "1.00");

        // "pricey" is the highest: $5 in, $25 out per million. 1M output = $25.
        assertThat(service.cost("some-new-model", 0, 1_000_000)).isEqualByComparingTo("25");
        assertThat(service.cost("claude-haiku-5-5", 0, 1_000_000)).isEqualByComparingTo("0.50");
    }

    @Test
    void recordSavesTheTokensTheModelTheFeatureAndTheCost() {
        ClaudeUsageService service = serviceAt(NOON, "1.00");

        service.record(Feature.WORD, "claude-haiku-5-5", 2_000, 1_000);

        ClaudeUsage row = repository.findAll().get(0);
        assertThat(row.getFeature()).isEqualTo(Feature.WORD);
        assertThat(row.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(row.getInputTokens()).isEqualTo(2_000);
        assertThat(row.getOutputTokens()).isEqualTo(1_000);
        assertThat(row.getCostUsd()).isEqualByComparingTo("0.0007"); // 0.0002 + 0.0005
        assertThat(row.getCreatedAt()).isEqualTo(NOON);
    }

    @Test
    void theBudgetIsReachedExactlyAtTheCapAndOnlyCountsTodayInUtc() {
        ClaudeUsageService service = serviceAt(NOON, "1.00");
        // Yesterday 23:59 UTC: a big spend that must not count today.
        repository.save(new ClaudeUsage(Instant.parse("2026-10-14T23:59:00Z"), "claude-haiku-5-5", Feature.SENTENCE, 0,
                100_000_000, new BigDecimal("50")));
        assertThat(service.budgetReached()).isFalse();

        repository.save(new ClaudeUsage(Instant.parse("2026-10-15T00:00:00Z"), "claude-haiku-5-5", Feature.SENTENCE, 0, 0,
                new BigDecimal("0.99999999")));
        assertThat(service.budgetReached()).isFalse();

        repository.save(new ClaudeUsage(Instant.parse("2026-10-15T01:00:00Z"), "claude-haiku-5-5", Feature.WORD, 0, 0,
                new BigDecimal("0.00000001")));
        assertThat(service.spentToday()).isEqualByComparingTo("1.00");
        assertThat(service.budgetReached()).isTrue();

        // After midnight UTC the next day, the budget is fresh.
        assertThat(serviceAt(Instant.parse("2026-10-16T00:00:01Z"), "1.00").budgetReached()).isFalse();
    }

    @Test
    void theReportSplitsEachDayByFeatureAndFillsEmptyDays() {
        ClaudeUsageService service = serviceAt(NOON, "1.00");
        service.record(Feature.SENTENCE, "claude-haiku-5-5", 1_000_000, 0); // $0.10 today
        repository.save(new ClaudeUsage(Instant.parse("2026-10-13T08:00:00Z"), "claude-haiku-5-5", Feature.WORD, 10, 20,
                new BigDecimal("0.25")));

        ClaudeUsageService.Report report = serviceAt(NOON, "1.00").report(3);

        assertThat(report.days()).extracting(ClaudeUsageService.Day::date)
                .containsExactly(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-13"));
        assertThat(report.todayUsd()).isEqualByComparingTo("0.10");
        assertThat(report.dailyBudgetUsd()).isEqualByComparingTo("1.00");
        assertThat(report.days().get(0).byFeature()).containsOnlyKeys("sentence");
        assertThat(report.days().get(1).total().calls()).isZero();
        ClaudeUsageService.Day thirteenth = report.days().get(2);
        assertThat(thirteenth.byFeature()).containsOnlyKeys("word");
        assertThat(thirteenth.total().inputTokens()).isEqualTo(10);
        assertThat(thirteenth.total().costUsd()).isEqualByComparingTo("0.25");
    }
}
