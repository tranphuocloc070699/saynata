package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonStringFieldReaderTest {

    /** Feeds the JSON in pieces of `size` characters and returns the first value found. */
    private static Optional<String> readInPieces(String json, int size) {
        JsonStringFieldReader reader = new JsonStringFieldReader("meaning");
        for (int start = 0; start < json.length(); start += size) {
            Optional<String> value = reader.feed(json.substring(start, Math.min(json.length(), start + size)));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    @Test
    void readsTheMeaningAfterIsEnglish() {
        String json = "{\"isEnglish\":true,\"meaning\":\"He did not want to go.\",\"words\":[],\"translation\":\"x\"}";

        assertThat(readInPieces(json, 1000)).contains("He did not want to go.");
    }

    @Test
    void givesTheValueAsSoonAsTheClosingQuoteArrives() {
        JsonStringFieldReader reader = new JsonStringFieldReader("meaning");

        assertThat(reader.feed("{\"isEnglish\":true,\"meaning\":\"It is")).isEmpty();
        assertThat(reader.feed(" raining.")).isEmpty();
        assertThat(reader.feed("\",\"words\"")).contains("It is raining.");
    }

    @Test
    void worksWhenTheJsonIsCutAtEveryPosition() {
        String json = "{\"isEnglish\":true,\"meaning\":\"She said \\\"hi\\\" and left.\",\"words\":[],\"translation\":\"\"}";

        for (int size = 1; size < 12; size++) {
            assertThat(readInPieces(json, size)).as("piece size %d", size).contains("She said \"hi\" and left.");
        }
    }

    @Test
    void handlesEscapedQuotesBackslashesAndNewlines() {
        String json = "{\"meaning\":\"a \\\"quote\\\", a back\\\\slash,\\nnew line, tab\\t, slash\\/\"}";

        assertThat(readInPieces(json, 3)).contains("a \"quote\", a back\\slash,\nnew line, tab\t, slash/");
    }

    @Test
    void handlesUnicodeEscapesAndRealUnicodeCharacters() {
        // é = e with acute, 😀 = a smiley (two surrogates), then raw characters.
        String json = "{\"meaning\":\"caf\\u00e9 \\ud83d\\ude00 — Việt Nam 😀\"}";

        for (int size : new int[] { 1, 2, 5, 100 }) {
            assertThat(readInPieces(json, size)).as("piece size %d", size).contains("café 😀 — Việt Nam 😀");
        }
    }

    @Test
    void anEscapedQuoteRightBeforeTheEndDoesNotCloseTheString() {
        String json = "{\"meaning\":\"ends with a quote\\\"\",\"words\":[]}";

        assertThat(readInPieces(json, 1)).contains("ends with a quote\"");
    }

    @Test
    void ignoresAMeaningKeyThatIsInsideTheWordsList() {
        // No top-level "meaning" at all: only word meanings, which are deeper.
        String json = "{\"isEnglish\":true,\"words\":[{\"word\":\"x\",\"meaning\":\"deep value\"}],\"translation\":\"t\"}";

        assertThat(readInPieces(json, 4)).isEmpty();
    }

    @Test
    void ignoresTheWordMeaningWhenTheTopLevelMeaningComesLater() {
        String json = "{\"words\":[{\"meaning\":\"deep\"}],\"meaning\":\"top\"}";

        assertThat(readInPieces(json, 2)).contains("top");
    }

    @Test
    void theTextOfAnotherValueIsNotMistakenForTheKey() {
        // The translation text is exactly "meaning", but it is a value, not a key.
        String json = "{\"translation\":\"meaning\",\"meaning\":\"real\"}";

        assertThat(readInPieces(json, 1)).contains("real");
    }

    @Test
    void emptyMeaningIsReturnedAsAnEmptyString() {
        assertThat(readInPieces("{\"isEnglish\":false,\"meaning\":\"\",\"words\":[]}", 5)).contains("");
    }

    @Test
    void allowsSpacesAndNewlinesBetweenTokens() {
        String json = "{\n  \"isEnglish\" : true,\n  \"meaning\" :   \"spaced out\" ,\n  \"words\": []\n}";

        assertThat(readInPieces(json, 3)).contains("spaced out");
    }

    @Test
    void givesTheValueOnlyOnce() {
        JsonStringFieldReader reader = new JsonStringFieldReader("meaning");

        assertThat(reader.feed("{\"meaning\":\"once\",\"meaning\":\"twice\"}")).contains("once");
        assertThat(reader.feed(",\"meaning\":\"again\"}")).isEmpty();
    }
}
