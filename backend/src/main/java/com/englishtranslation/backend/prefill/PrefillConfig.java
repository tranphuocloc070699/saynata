package com.englishtranslation.backend.prefill;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PrefillProperties.class)
public class PrefillConfig {
}
