package com.englishtranslation.backend.aipicture;

import com.englishtranslation.backend.admin.AdminWord;
import com.englishtranslation.backend.admin.AdminWordId;
import com.englishtranslation.backend.admin.AdminWordService;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.ClaudeGate;
import com.englishtranslation.backend.word.WordEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The prompt the admin edits before an AI picture is drawn (TASK-91). Two ways to get it:
 * <ul>
 * <li>{@link #prompt}: the newest media-ai scene of the sense, else the template (no Claude call, no cost);</li>
 * <li>{@link #newScene}: the wand. Claude Sonnet 5.5 writes a new scene (about $0.02), through the Claude gate and a
 * daily cap of its own.</li>
 * </ul>
 * The prompt is the INNER image prompt. The Codex wrapper text is added by the server when it runs Codex.
 */
@Service
public class AiPromptService {

    private static final Logger log = LoggerFactory.getLogger(AiPromptService.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** Where the prompt came from. */
    public static final String SCENE = "scene";
    public static final String TEMPLATE = "template";
    public static final String CLAUDE = "claude";

    /** The caps of the day, for the line "today 3 of 30 pictures · $0.04 of $1.00" in the dialog. */
    public record Usage(int picturesToday, int picturesPerDay, BigDecimal sceneUsdToday, BigDecimal sceneUsdPerDay) {
    }

    /**
     * @param prompt       the image prompt to show and edit
     * @param source       "scene" (the last media-ai scene), "template" or "claude" (the wand)
     * @param sceneVersion the pipeline prompt version of the saved scene (for example ai-v1), only for source "scene"
     */
    public record PromptAnswer(String prompt, String source, String sceneVersion, Usage usage) {
    }

    /** One sense of a word, with what the prompts need. */
    public record SenseView(AdminWordId id, String word, int position, WordEntry.Sense sense,
            List<WordEntry.Sense> others) {
    }

    private final AdminWordService words;
    private final JdbcClient jdbc;
    private final ClaudeService claude;
    private final ClaudeGate gate;
    private final ClaudeUsageService claudeUsage;
    private final AiPictureProperties properties;
    private final AiPictureJobs jobs;
    private final JsonMapper json;
    private final String sceneVersion;

    public AiPromptService(AdminWordService words, JdbcClient jdbc, ClaudeService claude, ClaudeGate gate,
            ClaudeUsageService claudeUsage, AiPictureProperties properties, AiPictureJobs jobs, JsonMapper json,
            @Value("${app.claude.scene-prompt-version:" + AiPrompts.VERSION + "}") String sceneVersion) {
        this.words = words;
        this.jdbc = jdbc;
        this.claude = claude;
        this.gate = gate;
        this.claudeUsage = claudeUsage;
        this.properties = properties;
        this.jobs = jobs;
        this.json = json;
        this.sceneVersion = sceneVersion;
    }

    /** The sense must exist, else 404. Works for bundled words (b-12) and backend words (w-45). */
    public SenseView sense(String wordId, int position) {
        AdminWordId id = AdminWordId.parse(wordId);
        AdminWord word = words.get(id.text()); // 404 when the word is missing
        if (position < 0 || word.entry() == null || position >= word.entry().senses().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No sense " + (position + 1) + " for word " + id.text() + ".");
        }
        List<WordEntry.Sense> others = new ArrayList<>();
        for (int i = 0; i < word.entry().senses().size(); i++) {
            if (i != position) {
                others.add(word.entry().senses().get(i));
            }
        }
        return new SenseView(id, word.word(), position, word.entry().senses().get(position), others);
    }

    /** GET: the newest media-ai scene as an image prompt, else the template. No Claude call. */
    public PromptAnswer prompt(String wordId, int position) {
        SenseView view = sense(wordId, position);
        if (view.id().bundled()) {
            Optional<SavedScene> saved = newestScene(view);
            if (saved.isPresent()) {
                SavedScene scene = saved.get();
                return new PromptAnswer(AiPrompts.imagePrompt(scene.scene(), scene.focus(), scene.avoid()), SCENE,
                        scene.version(), usage());
            }
        }
        return new PromptAnswer(AiPrompts.template(view.word(), view.sense()), TEMPLATE, null, usage());
    }

    /**
     * POST: Claude Sonnet writes a new scene. Checks the users' daily budget (ClaudeGate) and the wand's own daily
     * cap (ADMIN spend today under app.media.ai.scene-daily-usd) before the call. A method "none" (the sense cannot be
     * drawn) is a 422 not_drawable.
     */
    public PromptAnswer newScene(String wordId, int position) {
        SenseView view = sense(wordId, position);
        gate.checkBudget();
        if (claudeUsage.adminSpentToday().compareTo(properties.sceneDailyUsd()) >= 0) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_budget_reached",
                    "The wand has reached today's limit ($" + properties.sceneDailyUsd().toPlainString()
                            + "). Edit the prompt by hand, or try again tomorrow.");
        }
        String answer = claude.writePictureScene(AiPrompts.SCENE_SYSTEM,
                AiPrompts.sceneMessage(view.word(), view.sense(), view.others()));
        JsonNode scene = parseScene(answer);
        String method = text(scene, "method").strip().toLowerCase(Locale.ROOT);
        if (!AiPrompts.METHODS.contains(method)) {
            log.warn("Claude chose an unknown scene method '{}'", method);
            throw badScene();
        }
        if (method.equals("none")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "not_drawable",
                    "Claude says this meaning cannot be drawn in a way a learner would guess. Write the prompt by hand.");
        }
        String sceneText = text(scene, "scene");
        if (sceneText.isBlank()) {
            throw badScene();
        }
        return new PromptAnswer(AiPrompts.imagePrompt(sceneText, text(scene, "focus"), text(scene, "avoid")), CLAUDE,
                null, usage());
    }

    /** The caps of the day, in the shape the dialog shows. */
    public Usage usage() {
        return new Usage(jobs.countSince(claudeUsage.startOfToday()), properties.picturesPerDay(),
                claudeUsage.adminSpentToday(), properties.sceneDailyUsd());
    }

    // ---- helpers ----

    private record SavedScene(String scene, String focus, String avoid, String version) {
    }

    /** The newest media_generation row of the bundled sense that has a drawable scene (pipeline media-ai, TASK-82). */
    private Optional<SavedScene> newestScene(SenseView view) {
        List<String[]> rows = jdbc.sql("select g.scene_json, g.prompt_version from media_generation g"
                + " join dictionary_sense s on s.id = g.sense_id where s.word_id = :word and s.position = :position"
                + " and g.scene_json is not null and g.method is not null and g.method <> 'none'"
                + " order by g.created_at desc, g.id desc limit 1")
                .param("word", view.id().number()).param("position", view.position())
                .query((rs, n) -> new String[] {rs.getString("scene_json"), rs.getString("prompt_version")}).list();
        for (String[] row : rows) {
            try {
                JsonNode scene = readJson(row[0]);
                if (!text(scene, "scene").isBlank()) {
                    return Optional.of(new SavedScene(text(scene, "scene"), text(scene, "focus"), text(scene, "avoid"),
                            row[1]));
                }
            } catch (RuntimeException e) {
                log.warn("The saved scene of {} sense {} cannot be read, using the template: {}", view.id().text(),
                        view.position() + 1, e.toString());
            }
        }
        return Optional.empty();
    }

    /** A JSON object, also when the database gave it back as a JSON string that holds the object (H2). */
    private JsonNode readJson(String text) {
        JsonNode node = json.readTree(text);
        if (node.isString()) {
            node = json.readTree(node.asString());
        }
        return node;
    }

    /** The first {...} in Claude's text (it may add words around it), as pipeline parse_json does. */
    private JsonNode parseScene(String answer) {
        Matcher found = JSON_OBJECT.matcher(answer == null ? "" : answer);
        if (!found.find()) {
            log.warn("Claude's scene has no JSON: {}", answer);
            throw badScene();
        }
        try {
            JsonNode node = json.readTree(found.group());
            if (!node.isObject()) {
                throw badScene();
            }
            return node;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Claude's scene is not valid JSON: {}", answer);
            throw badScene();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static ApiException badScene() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "bad_scene", "Claude did not write a usable scene. Try again.");
    }
}
