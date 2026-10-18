package com.englishtranslation.backend.claude;

import com.anthropic.models.messages.OutputConfig;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * The Claude settings. {@code baseUrl} and {@code authToken} point the SDK at an Anthropic-compatible gateway
 * instead of api.anthropic.com (empty = Anthropic itself with {@code apiKey}). {@code sceneModel} writes the AI
 * picture scene; {@code effort} is sent with the sentence and word calls, empty = not sent (older models refuse it).
 */
@ConfigurationProperties("app.claude")
public record ClaudeProperties(String apiKey, String model, String sentencePromptVersion, String wordPromptVersion,
        String baseUrl, String authToken, String sceneModel, String effort) {

    public static final String DEFAULT_SCENE_MODEL = "claude-sonnet-5-5";

    @ConstructorBinding
    public ClaudeProperties {
        if (sceneModel == null || sceneModel.isBlank()) {
            sceneModel = DEFAULT_SCENE_MODEL;
        }
    }

    /** The old shape, for tests: Anthropic itself, the default scene model, low effort. */
    public ClaudeProperties(String apiKey, String model, String sentencePromptVersion, String wordPromptVersion) {
        this(apiKey, model, sentencePromptVersion, wordPromptVersion, null, null, null, "low");
    }

    /** The effort to send, or empty when {@code effort} is blank. */
    public Optional<OutputConfig.Effort> effortLevel() {
        if (effort == null || effort.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(OutputConfig.Effort.of(effort.trim().toLowerCase()));
    }
}
