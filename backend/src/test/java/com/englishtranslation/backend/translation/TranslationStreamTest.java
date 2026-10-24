package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.NotEnglishException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** POST /api/translations/stream (TASK-65), with a fake streaming Claude. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class TranslationStreamTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SentenceTranslationRepository repository;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private static final Explanation EXPLANATION = new Explanation(
            "He did not want to leave.",
            List.of(new Explanation.Word("reluctant", "adjective", "not wanting to do something")),
            "");

    /** Claude's JSON answer, cut into pieces the way a stream would. */
    private static final List<String> PIECES = List.of(
            "{\"isEnglish\":tr", "ue,\"summary\":\"He did not ", "want to leave.\",\"tra",
            "nslation\":\"...\"}");

    /** Makes the fake Claude stream PIECES and return `result`. */
    private void claudeStreams(String text, Explanation result) {
        // The language line comes from a second, small call (TASK-106.6).
        when(claude.translateLines(any(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of("Anh ấy không muốn đi."));
        when(claude.explainSentenceStreaming(eq(text), any())).thenAnswer(call -> {
            Consumer<String> onText = call.getArgument(1);
            PIECES.forEach(onText);
            return result;
        });
    }

    /** Sends the request, waits for the stream to end, and returns the whole body. */
    private String streamBody(String text) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/translations/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(10_000);
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The event names, in the order they were sent. */
    private static List<String> eventNames(String body) {
        List<String> names = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.startsWith("event:")) {
                names.add(line.substring("event:".length()).trim());
            }
        }
        return names;
    }

    /** What an old app sees: it ignores every event it does not know (here, meaning-delta). */
    private static List<String> eventsAnOldAppReads(String body) {
        return eventNames(body).stream().filter(name -> !name.equals("meaning-delta")).toList();
    }

    @Test
    void deltasComeFirstThenTheFullMeaningThenDone() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        String body = streamBody("He was reluctant to leave");

        assertThat(eventNames(body)).containsExactly("meaning-delta", "meaning-delta", "meaning", "done");
        assertThat(body).contains("data:{\"meaning\":\"He did not want to leave.\"}");
    }

    @Test
    void theDeltasAddUpToTheFullMeaning() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        String body = streamBody("He was reluctant to leave");

        assertThat(body).contains("event:meaning-delta\ndata:{\"text\":\"He did not \"}");
        assertThat(body).contains("event:meaning-delta\ndata:{\"text\":\"want to leave.\"}");
    }

    @Test
    void anOldAppThatIgnoresDeltasStillSeesMeaningThenDone() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        String body = streamBody("He was reluctant to leave");

        assertThat(eventsAnOldAppReads(body)).containsExactly("meaning", "done");
    }

    @Test
    void doneHasTheSameJsonAsTheOldEndpoint() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        String body = streamBody("He was reluctant to leave");

        String doneLine = body.lines().filter(line -> line.startsWith("data:{\"text\":\"He was")).findFirst().orElseThrow();
        assertThat(doneLine).isEqualTo("data:{\"text\":\"He was reluctant to leave\",\"targetLang\":\"vi\","
                + "\"explanation\":{\"meaning\":\"He did not want to leave.\","
                + "\"words\":[{\"word\":\"reluctant\",\"partOfSpeech\":\"adjective\",\"meaning\":\"not wanting to do something\"}],"
                + "\"better\":null,\"betterWhy\":null,\"translation\":\"Anh ấy không muốn đi.\"},\"cached\":false}");
    }

    @Test
    void theAnswerIsSavedSoTheNextRequestIsOnlyDone() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        streamBody("He was reluctant to leave");
        String second = streamBody("He was reluctant to leave");

        assertThat(repository.count()).isEqualTo(1);
        assertThat(eventNames(second)).containsExactly("done");
        assertThat(second).contains("\"cached\":true");
    }

    @Test
    void aCacheHitSendsOnlyDoneAndNeverCallsClaude() throws Exception {
        // Fill the cache through the normal (non-streaming) path first.
        when(claude.explainSentence("I love you")).thenReturn(EXPLANATION);
        mockMvc.perform(post("/api/translations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"I love you\", \"targetLang\": \"vi\"}")).andExpect(status().isOk());

        String body = streamBody("I love you");

        assertThat(eventNames(body)).containsExactly("done");
        verify(claude, never()).explainSentenceStreaming(any(), any());
    }

    @Test
    void personalTextStillStreamsButIsNeverCached() throws Exception {
        String text = "Email me at john@example.com";
        claudeStreams(text, EXPLANATION);

        String first = streamBody(text);
        String second = streamBody(text);

        assertThat(eventsAnOldAppReads(first)).containsExactly("meaning", "done");
        assertThat(eventsAnOldAppReads(second)).containsExactly("meaning", "done");
        assertThat(repository.count()).isZero();
    }

    @Test
    void textThatIsNotEnglishGivesAnErrorEventAndIsNotCached() throws Exception {
        // A not-English answer has an empty meaning, so no meaning event is sent.
        when(claude.explainSentenceStreaming(eq("asdkj qwe zzxq"), any())).thenAnswer(call -> {
            Consumer<String> onText = call.getArgument(1);
            onText.accept("{\"isEnglish\":false,\"meaning\":\"\",\"words\":[],\"translation\":\"\"}");
            throw new NotEnglishException();
        });

        String body = streamBody("asdkj qwe zzxq");

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).contains("data:{\"status\":422,\"message\":\"This doesn't look like English. "
                + "Type an English word or sentence.\"}");
        assertThat(repository.count()).isZero();
    }

    @Test
    void aFailureAfterTheMeaningGivesAnErrorEvent() throws Exception {
        when(claude.explainSentenceStreaming(eq("Hello there"), any())).thenAnswer(call -> {
            Consumer<String> onText = call.getArgument(1);
            PIECES.forEach(onText);
            throw new IllegalStateException("connection lost");
        });

        String body = streamBody("Hello there");

        assertThat(eventsAnOldAppReads(body)).containsExactly("meaning", "error");
        assertThat(body).contains("data:{\"status\":502,\"message\":\"The translation service is not available. Try again later.\"}");
        assertThat(repository.count()).isZero();
    }

    @Test
    void theErrorEventDoesNotShowInternalDetails() throws Exception {
        when(claude.explainSentenceStreaming(eq("Hello there"), any()))
                .thenThrow(new IllegalStateException("secret internal detail"));

        String body = streamBody("Hello there");

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).doesNotContain("secret internal detail");
    }

    @Test
    void theResponseCannotBeBufferedByAProxy() throws Exception {
        claudeStreams("He was reluctant to leave", EXPLANATION);

        MvcResult started = mockMvc.perform(post("/api/translations/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"He was reluctant to leave\", \"targetLang\": \"vi\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
        started.getAsyncResult(10_000);

        assertThat(started.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void badInputIsStillRejectedWithA400() throws Exception {
        mockMvc.perform(post("/api/translations/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest());
    }
}
