package com.englishtranslation.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.claude.api-key=test-key")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
