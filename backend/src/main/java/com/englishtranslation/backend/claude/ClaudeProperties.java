package com.englishtranslation.backend.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.claude")
public record ClaudeProperties(String apiKey, String model, String sentencePromptVersion, String wordPromptVersion) {
}
