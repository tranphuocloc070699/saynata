package com.englishtranslation.backend.limits;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LimitProperties.class)
public class LimitsConfig {

    /** One clock for everything that counts time (rate limits, the daily budget, codes), so tests can replace it. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
