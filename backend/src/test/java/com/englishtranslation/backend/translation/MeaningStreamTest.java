package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeaningStreamTest {

    private static final long MS = 1_000_000L;

    private final List<String> log = new ArrayList<>();
    private long now = 1_000 * MS;

    private MeaningStream newStream() {
        return new MeaningStream(delta -> log.add("delta:" + delta), meaning -> log.add("meaning:" + meaning), () -> now);
    }

    @Test
    void thePiecesInsideOneThrottleWindowAreJoinedIntoOneDelta() {
        MeaningStream stream = newStream();

        stream.feed("{\"meaning\":\"He "); // the first one goes out at once
        now += 10 * MS;
        stream.feed("did "); // too soon: waits
        now += 10 * MS;
        stream.feed("not "); // still waiting
        now += 40 * MS; // 60 ms since the last send
        stream.feed("go");

        assertThat(log).containsExactly("delta:He ", "delta:did not go");
    }

    @Test
    void whatIsStillWaitingIsSentBeforeTheFullMeaning() {
        MeaningStream stream = newStream();

        stream.feed("{\"meaning\":\"Hi ");
        now += 5 * MS;
        stream.feed("there\",\"words\":[]}");

        assertThat(log).containsExactly("delta:Hi ", "delta:there", "meaning:Hi there");
    }

    @Test
    void deltasAddUpToTheFullMeaningForAnyPieceSize() {
        String json = "{\"isEnglish\":true,\"meaning\":\"Caf\\u00e9 \\ud83d\\ude00 \\\"ok\\\"\\n!\",\"words\":[]}";
        String expected = "Café 😀 \"ok\"\n!";
        for (int size = 1; size <= json.length(); size++) {
            log.clear();
            MeaningStream stream = newStream();
            for (int i = 0; i < json.length(); i += size) {
                now += 7 * MS;
                stream.feed(json.substring(i, Math.min(json.length(), i + size)));
            }
            String text = log.stream().filter(e -> e.startsWith("delta:")).map(e -> e.substring(6))
                    .reduce("", String::concat);
            assertThat(text).as("size %d", size).isEqualTo(expected);
            assertThat(log.get(log.size() - 1)).as("size %d", size).isEqualTo("meaning:" + expected);
        }
    }

    @Test
    void aBlankMeaningSendsNothing() {
        MeaningStream stream = newStream();

        stream.feed("{\"isEnglish\":false,\"meaning\":\"\",\"words\":[],\"translation\":\"\"}");

        assertThat(log).isEmpty();
    }

    @Test
    void aFastStreamMakesFewEvents() {
        MeaningStream stream = newStream();
        stream.feed("{\"meaning\":\"");
        for (int i = 0; i < 1000; i++) {
            now += MS; // 1 ms per piece: 1000 pieces in one second
            stream.feed("x");
        }
        stream.feed("\"}");

        // About one delta per 50 ms (20 for one second), plus the first, the last, and the meaning.
        assertThat(log.size()).isLessThan(30);
    }
}
