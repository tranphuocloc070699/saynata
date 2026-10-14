package com.englishtranslation.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** With ADMIN_ENABLED=false the app starts without any admin secret, and /api/admin is closed. */
@SpringBootTest(properties = { "app.claude.api-key=test-key", "app.admin.enabled=false", "app.admin.jwt-secret=" })
@AutoConfigureMockMvc
class AdminDisabledTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void startsWithoutSecretAndAdminIsClosed() throws Exception {
        mockMvc.perform(get("/api/admin/words")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
