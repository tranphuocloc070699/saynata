package com.englishtranslation.backend.aipicture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.admin.LoginRateLimiter;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** TASK-91: with the default settings (AI_PICTURES_ENABLED=false) nothing is drawn and no Codex is needed. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AiPictureDisabledTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    LoginRateLimiter loginLimiter;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    ImageDrawer drawer;

    Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        bundled.deleteAll();
        users.deleteAll();
        loginLimiter.reset();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from ai_picture_job");
        bundled.deleteAll();
    }

    @Test
    void drawingIs503AiUnavailableAndNoJobIsMade() throws Exception {
        DictionaryWord word = new DictionaryWord("run", 1, "/run/");
        DictionarySense sense = new DictionarySense(word, 0);
        sense.setText("verb", "A1", "to move fast", "nghia", "[]", "[]");
        word.getSenses().add(sense);
        DictionaryWord saved = bundled.save(word);

        mockMvc.perform(post("/api/admin/words/b-" + saved.getId() + "/senses/0/media/ai-picture").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"A runner\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ai_unavailable"));

        verify(drawer, never()).draw(anyString());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from ai_picture_job", Integer.class))
                .isZero();
    }
}
