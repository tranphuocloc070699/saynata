package com.englishtranslation.backend.translation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class TranslationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClaudeService claude;

    @Test
    void blankTextIsRejected() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\", \"targetLang\": \"xx\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void languageCodeIsReadAndReturned() throws Exception {
        when(claude.explainSentence("hello", TargetLanguage.JA))
                .thenReturn(new Explanation("A greeting.", List.of(), "こんにちは"));

        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\", \"targetLang\": \"ja\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLang").value("ja"))
                .andExpect(jsonPath("$.explanation.meaning").value("A greeting."))
                .andExpect(jsonPath("$.explanation.words").isArray())
                .andExpect(jsonPath("$.explanation.translation").value("こんにちは"));
    }

    @Test
    void wordWithSpacesIsRejected() throws Exception {
        mockMvc.perform(get("/api/words/two words"))
                .andExpect(status().isBadRequest());
    }
}
