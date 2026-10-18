package com.englishtranslation.backend.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ ClaudeProperties.class, UsageProperties.class })
public class ClaudeConfig {

    @Bean
    public AnthropicClient anthropicClient(ClaudeProperties properties) {
        boolean hasKey = properties.apiKey() != null && !properties.apiKey().isBlank();
        boolean hasToken = properties.authToken() != null && !properties.authToken().isBlank();
        if (!hasKey && !hasToken) {
            throw new IllegalStateException("Set CLAUDE_API_KEY, or CLAUDE_AUTH_TOKEN for a gateway.");
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
        // A gateway (CLAUDE_BASE_URL) that speaks the Anthropic API takes a Bearer token instead of x-api-key.
        if (hasToken) {
            builder.authToken(properties.authToken());
        } else {
            builder.apiKey(properties.apiKey());
        }
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        return builder
                // Keep a few HTTPS connections open between calls, so a new sentence does not pay for a new
                // TCP and TLS handshake (TASK-75). The SDK default is 5 connections for 5 minutes.
                .maxIdleConnections(5)
                .keepAliveDuration(Duration.ofMinutes(10))
                .build();
    }
}
