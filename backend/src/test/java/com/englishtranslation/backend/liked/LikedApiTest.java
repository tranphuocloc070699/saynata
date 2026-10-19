package com.englishtranslation.backend.liked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.auth.AccessTokenService;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET / PUT / DELETE /api/liked: per user, idempotent, validated, and gone with the account. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class LikedApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    LikedItemRepository liked;
    @Autowired
    AccessTokenService accessTokens;

    @MockitoBean
    ClaudeService claude;

    private String tokenA;
    private String tokenB;
    private long idA;

    @BeforeEach
    void setUp() {
        liked.deleteAll();
        users.deleteAll();
        idA = users.save(User.appUser("liker-a@example.com")).getId();
        long idB = users.save(User.appUser("liker-b@example.com")).getId();
        tokenA = accessTokens.createToken(idA);
        tokenB = accessTokens.createToken(idB);
    }

    private ResultActions like(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/liked").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(String token) throws Exception {
        return mockMvc.perform(get("/api/liked").header("Authorization", "Bearer " + token));
    }

    private static String body(String kind, String text, String preview) {
        return "{\"kind\":\"" + kind + "\",\"text\":\"" + text + "\",\"preview\":\"" + preview + "\"}";
    }

    @Test
    void listIsEmptyAtFirst() throws Exception {
        list(tokenA).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void likeThenListNewestFirst() throws Exception {
        like(tokenA, body("word", "Run", "to move fast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("word"))
                .andExpect(jsonPath("$.text").value("Run"))
                .andExpect(jsonPath("$.preview").value("to move fast"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        Thread.sleep(5);
        like(tokenA, body("sentence", "How are you?", "A greeting")).andExpect(status().isOk());

        list(tokenA).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("How are you?"))
                .andExpect(jsonPath("$.items[1].text").value("Run"));
    }

    @Test
    void likingTheSameWordAgainKeepsOneRowAndUpdatesThePreview() throws Exception {
        like(tokenA, body("word", "Run", "old")).andExpect(status().isOk());
        like(tokenA, body("word", "  run ", "new")).andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value("new"));

        list(tokenA).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].preview").value("new"));
    }

    @Test
    void sentencesKeepTheirCaseSoTheyAreDifferentItems() throws Exception {
        like(tokenA, body("sentence", "US is big.", "x")).andExpect(status().isOk());
        like(tokenA, body("sentence", "us is big.", "x")).andExpect(status().isOk());
        // The same text as a word is a different item too.
        like(tokenA, body("word", "us", "x")).andExpect(status().isOk());

        list(tokenA).andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void deleteUsesTheNormalizedKeyAndIsOkWhenNothingIsThere() throws Exception {
        like(tokenA, body("word", "Run", "x")).andExpect(status().isOk());

        mockMvc.perform(delete("/api/liked").param("kind", "word").param("text", " RUN")
                .header("Authorization", "Bearer " + tokenA)).andExpect(status().isNoContent());
        list(tokenA).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(delete("/api/liked").param("kind", "word").param("text", "run")
                .header("Authorization", "Bearer " + tokenA)).andExpect(status().isNoContent());
    }

    @Test
    void aUserOnlySeesAndRemovesTheirOwnRows() throws Exception {
        like(tokenA, body("word", "run", "x")).andExpect(status().isOk());
        like(tokenB, body("word", "run", "mine")).andExpect(status().isOk());

        mockMvc.perform(delete("/api/liked").param("kind", "word").param("text", "run")
                .header("Authorization", "Bearer " + tokenB)).andExpect(status().isNoContent());

        list(tokenB).andExpect(jsonPath("$.items").isEmpty());
        list(tokenA).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void aGuestGets401WithSignInRequiredOnAllThreeEndpoints() throws Exception {
        mockMvc.perform(get("/api/liked")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("sign_in_required"));
        mockMvc.perform(put("/api/liked").contentType(MediaType.APPLICATION_JSON).content(body("word", "run", "x")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("sign_in_required"));
        mockMvc.perform(delete("/api/liked").param("kind", "word").param("text", "run"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("sign_in_required"));
        mockMvc.perform(get("/api/liked").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("sign_in_required"));
    }

    @Test
    void badInputGives400() throws Exception {
        like(tokenA, body("phrase", "run", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_kind"));
        like(tokenA, body("word", "   ", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_text"));
        like(tokenA, body("sentence", "a".repeat(501), "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_text"));
        like(tokenA, body("word", "run", "p".repeat(301))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_preview"));
        like(tokenA, "{\"text\":\"run\"}").andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/liked").param("kind", "bogus").param("text", "run")
                .header("Authorization", "Bearer " + tokenA)).andExpect(status().isBadRequest());

        // The limits themselves are allowed.
        like(tokenA, body("sentence", "a".repeat(500), "p".repeat(300))).andExpect(status().isOk());
    }

    @Test
    void aUserCanKeepAtMost5000Items() throws Exception {
        for (int i = 0; i < LikedService.MAX_ITEMS; i++) {
            liked.save(new LikedItem(idA, "word", "w" + i, "w" + i, "", java.time.Instant.now()));
        }

        like(tokenA, body("word", "onemore", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("liked_limit_reached"));
        // Liking an item that is already there still works at the limit.
        like(tokenA, body("word", "w1", "again")).andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountDeletesTheLikedRows() throws Exception {
        like(tokenA, body("word", "run", "x")).andExpect(status().isOk());
        assertThat(liked.countByUserId(idA)).isEqualTo(1);

        mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + tokenA)).andExpect(status().isNoContent());

        assertThat(liked.countByUserId(idA)).isZero();
    }
}
