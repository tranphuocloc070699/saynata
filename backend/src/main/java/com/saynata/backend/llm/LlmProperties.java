package com.saynata.backend.llm;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** The "app.llm.*" settings in application.yml. */
@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
                @DefaultValue("fake") String provider,
                /**
                 * Money we allow to spend per day (UTC). When it is reached, new LLM calls stop
                 * until tomorrow.
                 */
                @DefaultValue("1.00") @Positive BigDecimal dailyBudgetUsd,
                /**
                 * Flat price we count for one call. Replace with real token cost when the real
                 * client exists.
                 */
                @DefaultValue("0.001") @Positive BigDecimal costPerCallUsd,
                /** How many new LLM calls one IP may start per hour. */
                @DefaultValue("30") @Positive int maxCallsPerIpPerHour) {
}
