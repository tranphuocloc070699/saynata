package com.saynata.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the "app.*" settings in application.yml.
 * If a value is blank, the app refuses to start (fail fast).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        @NotEmpty List<String> corsAllowedOrigins) {
}
