package com.englishtranslation.backend.aipicture;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the AI pictures the admin draws on the server (TASK-91). All of them are app.media.ai.* in
 * application.properties and can be set from the environment.
 *
 * @param enabled        env AI_PICTURES_ENABLED. false (default) = no picture is drawn (503 ai_unavailable), so tests and
 *                       local development do not need Codex. The production compose file turns it on.
 * @param picturesPerDay env AI_PICTURES_PER_DAY. The most pictures started per UTC day. It protects the ChatGPT plan.
 * @param sceneDailyUsd  env AI_SCENE_DAILY_USD. The most the wand (Claude writes a new scene) may spend per UTC day.
 * @param codexCommand   the Codex program (default "codex", found through PATH)
 * @param codexHome      env CODEX_HOME. The folder with auth.json; Codex saves the pictures in generated_images there.
 * @param sandbox        the Codex sandbox, "read-only" by default. If that fails inside Docker, try "workspace-write".
 * @param timeout        how long one Codex run may take before it is stopped
 */
@ConfigurationProperties("app.media.ai")
public record AiPictureProperties(boolean enabled, int picturesPerDay, BigDecimal sceneDailyUsd, String codexCommand,
        String codexHome, String sandbox, Duration timeout) {
}
