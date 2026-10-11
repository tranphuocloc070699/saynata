package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExplanationTest {

    @Test
    void jsonHasTheEnglishExplanationFirstAndTheTargetLanguageTextLast() {
        Explanation explanation = new Explanation(
                "The speaker did not want to go.",
                List.of(new Explanation.Word("reluctant", "adjective", "not wanting to do something")),
                "Tôi miễn cưỡng.");

        String json = JsonMapper.builder().build().writeValueAsString(explanation);

        assertThat(json).startsWith("{\"meaning\":");
        assertThat(json.indexOf("\"words\"")).isLessThan(json.indexOf("\"translation\""));
    }
}
