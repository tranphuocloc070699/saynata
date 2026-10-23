package com.englishtranslation.backend.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummaryFormatTest {

    @Test
    void aOneLineSummaryStaysAsItIs() {
        assertThat(SummaryFormat.clean("I do not know what to choose.")).isEqualTo("I do not know what to choose.");
    }

    @Test
    void aGistAndDashBulletsWithBoldStay() {
        String summary = "Your **career** is at risk.\n- Use **private distribution**.\n- Leave if it goes on.";

        assertThat(SummaryFormat.clean(summary)).isEqualTo(summary);
    }

    @Test
    void otherBulletsBecomeDashesAndOnlyThreeAreKept() {
        assertThat(SummaryFormat.clean("Gist.\n* one\n• two\n1. three\n2) four"))
                .isEqualTo("Gist.\n- one\n- two\n- three");
    }

    @Test
    void headingsEmptyLinesAndWindowsLineEndsGo() {
        assertThat(SummaryFormat.clean("# Gist\r\n\r\n-   one  \r\n")).isEqualTo("Gist\n- one");
    }

    @Test
    void aLineWithAnUnclosedBoldLosesItsMarkers() {
        assertThat(SummaryFormat.clean("A **ban could hurt.\n- **Keep** this.")).isEqualTo("A ban could hurt.\n- **Keep** this.");
    }

    @Test
    void boldAtTheStartOfALineIsNotABullet() {
        assertThat(SummaryFormat.clean("**Apple** bans these apps.")).isEqualTo("**Apple** bans these apps.");
    }

    @Test
    void emptyStaysEmpty() {
        assertThat(SummaryFormat.clean("")).isEmpty();
        assertThat(SummaryFormat.clean(null)).isEmpty();
    }
}
