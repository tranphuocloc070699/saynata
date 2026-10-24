package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BetterCheckTest {

    @Test
    void keepsSmallFixesOfShortLines() {
        assertThat(BetterCheck.keeps("I already do it.", "I've already done it.")).isTrue();
        assertThat(BetterCheck.keeps("Please open the light, it's too dark.", "Please turn on the light. It's too dark."))
                .isTrue();
        assertThat(BetterCheck.keeps("She don't like apples.", "She doesn't like apples.")).isTrue();
    }

    @Test
    void dropsAFixThatOnlyChangesCaseOrPunctuation() {
        assertThat(BetterCheck.keeps("how are you", "How are you?")).isFalse();
        assertThat(BetterCheck.keeps("I don’t know", "I don't know.")).isFalse();
    }

    @Test
    void dropsARewrite() {
        assertThat(BetterCheck.keeps("The committee postponed its decision until further evidence was available.",
                "The group waited to decide until they had more proof.")).isFalse();
    }

    @Test
    void dropsAnyFixForTextLongerThan40Words() {
        String long41 = "word ".repeat(40) + "end";
        assertThat(BetterCheck.keeps(long41, long41.replace("end", "ends"))).isFalse();
    }

    @Test
    void allowsAtLeastThreeChangesThenFortyPercent() {
        assertThat(BetterCheck.allowedChanges(4)).isEqualTo(3);
        assertThat(BetterCheck.allowedChanges(20)).isEqualTo(8);
    }

    @Test
    void applyRemovesTheFixAndItsReason() {
        Explanation explanation = new Explanation("Hi.", List.of(), "How are you?", "Add a question mark.", "Chào");

        assertThat(BetterCheck.apply("how are you", explanation)).isEqualTo(new Explanation("Hi.", List.of(), "Chào"));
    }
}
