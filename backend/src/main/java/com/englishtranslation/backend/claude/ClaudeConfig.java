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
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Set the CLAUDE_API_KEY environment variable.");
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                // Keep a few HTTPS connections open between calls, so a new sentence does not pay for a new
                // TCP and TLS handshake (TASK-75). The SDK default is 5 connections for 5 minutes.
                .maxIdleConnections(5)
                .keepAliveDuration(Duration.ofMinutes(10))
                .build();
    }
}
