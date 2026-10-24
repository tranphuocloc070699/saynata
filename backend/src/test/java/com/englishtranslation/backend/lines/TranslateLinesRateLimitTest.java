package com.englishtranslation.backend.lines;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.limits.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** TASK-106.6: /api/translate-lines has the per-IP limit of the other public reads (cachedOnly, so no Claude call). */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true",
        "app.limits.public-read-per-ip.max=3"
})
@AutoConfigureMockMvc
class TranslateLinesRateLimitTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    RateLimiter rateLimiter;

    @BeforeEach
    void resetLimits() {
        rateLimiter.reset();
    }

    private ResultActions send(String ip) throws Exception {
        return mockMvc.perform(post("/api/translate-lines").param("cachedOnly", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetLang\":\"vi\",\"texts\":[\"hello\"]}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    @Test
    void translateLinesIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("203.0.113.77").andExpect(status().isOk());
        }
        send("203.0.113.77").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }
}
