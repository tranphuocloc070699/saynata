package com.englishtranslation.backend.translation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.NotEnglishException;
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
    void blankTextSaysWhatIsWrong() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Text must be 1-500 characters."));
    }

    @Test
    void textOver500CharactersSaysWhatIsWrong() throws Exception {
        String longText = "a".repeat(501);

        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"" + longText + "\", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Text must be 1-500 characters."));
    }

    @Test
    void textOf500CharactersIsAccepted() throws Exception {
        String text = "a".repeat(500);
        when(claude.explainSentence(text, TargetLanguage.VI))
                .thenReturn(new Explanation("A long text.", List.of(), "Một văn bản dài."));

        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void missingLanguageSaysWhatIsWrong() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a target language."));
    }

    @Test
    void unknownLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\", \"targetLang\": \"xx\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thaiIsNoLongerSupported() throws Exception {
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"hello\", \"targetLang\": \"th\"}"))
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
    void textThatIsNotEnglishGivesA422WithAClearMessage() throws Exception {
        when(claude.explainSentence("asdkj qwe zzxq", TargetLanguage.VI)).thenThrow(new NotEnglishException());

        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"asdkj qwe zzxq\", \"targetLang\": \"vi\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("This doesn't look like English. Type an English word or sentence."));
    }

    @Test
    void wordWithMarksIsRejected() throws Exception {
        // Spaces are allowed now: a phrase is answered from the cache only (PhraseLookupTest).
        mockMvc.perform(get("/api/words/two, words"))
                .andExpect(status().isBadRequest());
    }
}
