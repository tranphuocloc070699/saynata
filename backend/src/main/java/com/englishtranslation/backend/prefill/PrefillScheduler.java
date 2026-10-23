package com.englishtranslation.backend.prefill;

import com.englishtranslation.backend.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Starts a prefill run by itself on the {@code app.prefill.cron} schedule (UTC). An empty cron means off.
 * The night run answers only the sources in {@code app.prefill.cron-sources} (default: the words users typed).
 * What the developer adds is run at once by hand (TASK-83.12).
 * It has its own small scheduler, so it does not change how the rest of the app works.
 */
@Configuration
public class PrefillScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrefillScheduler.class);

    private final PrefillProperties properties;
    private final PrefillRunner runner;
    private ThreadPoolTaskScheduler scheduler;

    public PrefillScheduler(PrefillProperties properties, PrefillRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        String cron = properties.cron();
        if (cron.isEmpty()) {
            log.info("Prefill schedule is off (PREFILL_CRON is empty)");
            return;
        }
        if (properties.cronSources().isEmpty()) {
            log.error("PREFILL_CRON_SOURCES is empty. Prefill is off (an empty list would mean every source at night).");
            return;
        }
        if (!CronExpression.isValidExpression(cron)) {
            log.error("PREFILL_CRON '{}' is not a valid cron (six fields, for example '0 0 20 * * *'). Prefill is off.", cron);
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("prefill-cron-");
        scheduler.initialize();
        scheduler.schedule(this::runScheduled, new CronTrigger(cron, ZoneOffset.UTC));
        log.info("Prefill runs on the schedule '{}' (UTC) for sources {}, cap ${} per run", cron,
                properties.cronSources(), properties.maxUsdPerRun());
    }

    /** One scheduled run, on the scheduler thread. An error must never stop the schedule. */
    void runScheduled() {
        try {
            runner.runNow("cron", null, null, properties.cronSources());
        } catch (ApiException e) {
            log.info("Scheduled prefill skipped: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Scheduled prefill failed", e);
        }
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
