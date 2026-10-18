package com.englishtranslation.backend.aipicture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.englishtranslation.backend.word.WordEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The prompt texts of the AI picture (TASK-91): the building blocks and the match with the pipeline's media_ai.py. */
class AiPromptsTest {

    private static WordEntry.Sense sense(String pos, String meaning, WordEntry.Example... examples) {
        return new WordEntry.Sense(pos, "A1", meaning, "nghia", List.of(examples), List.of());
    }

    @Test
    void theKidTextIsAddedAfterTheFirstTheKidOnly() {
        String prompt = AiPrompts.imagePrompt("The kid drinks water. Then the kid sleeps.", "the glass", "a bottle");

        assertThat(prompt).startsWith("The kid (about 8 years old, short brown hair, sage green T-shirt, deep teal jeans,"
                + " white sneakers) drinks water. Then the kid sleeps.");
        assertThat(prompt).contains(" Main focus: the glass. Must not appear: a bottle.\n" + AiPrompts.STYLE);
        // Only one description of the kid.
        assertThat(prompt.split("about 8 years old", -1)).hasSize(2);
    }

    @Test
    void aSceneWithoutTheKidKeepsItsText() {
        String prompt = AiPrompts.imagePrompt("A red apple on a table.", "the apple", "");

        assertThat(prompt).isEqualTo("A red apple on a table. Main focus: the apple. Must not appear: .\n" + AiPrompts.STYLE);
    }

    @Test
    void theCodexWrapperEndsWithTheImagePromptWordForWord() {
        String codex = AiPrompts.codexPrompt("MY PROMPT");

        assertThat(codex).startsWith("Use your built-in image generation tool exactly ONCE with the IMAGE PROMPT below");
        assertThat(codex).endsWith("\n\nIMAGE PROMPT:\nMY PROMPT");
    }

    @Test
    void theTemplateHasTheWordTheMeaningTheEasyExampleAndEndsWithTheStyle() {
        WordEntry.Sense sense = sense("verb", "to move fast on your feet",
                new WordEntry.Example("medium", "She runs every morning."), new WordEntry.Example("easy", "I run."));

        assertThat(AiPrompts.template("run", sense)).isEqualTo(
                "A picture that shows the meaning of the English word \"run\" (verb) for a language learner.\n"
                        + "Meaning: to move fast on your feet\n"
                        + "Example sentence (do not write it in the picture): \"I run.\"\n"
                        + "Show this meaning clearly, so a learner can guess the word from the picture alone.\n"
                        + AiPrompts.STYLE);
    }

    @Test
    void theTemplateLeavesOutTheExampleLineWhenThereIsNone() {
        String prompt = AiPrompts.template("run", sense("verb", "to move fast"));

        assertThat(prompt).doesNotContain("Example sentence");
        assertThat(prompt).endsWith(AiPrompts.STYLE);
    }

    @Test
    void theEasyExampleFallsBackToTheFirstOne() {
        assertThat(AiPrompts.easyExample(sense("noun", "x", new WordEntry.Example("medium", "First."),
                new WordEntry.Example("harder", "Second.")))).isEqualTo("First.");
    }

    @Test
    void theSceneMessageListsTheOtherSensesLikePythonJsonDumps() {
        WordEntry.Sense main = sense("noun", "a place for money", new WordEntry.Example("easy", "I go to the bank."));
        WordEntry.Sense other = sense("noun", "the side of a river \"edge\"");

        assertThat(AiPrompts.sceneMessage("bank", main, List.of(other))).isEqualTo("Word: \"bank\" (noun)\n"
                + "This sense: a place for money\nExample: I go to the bank.\n"
                + "Other senses of the word (do NOT show these): "
                + "[{\"pos\": \"noun\", \"meaning\": \"the side of a river \\\"edge\\\"\"}]");
        assertThat(AiPrompts.sceneMessage("bank", main, List.of()))
                .endsWith("Other senses of the word (do NOT show these): []");
    }

    /** The texts must stay word for word the same as the pipeline's, so the pictures match (PROMPT_VERSION ai-v1). */
    @Test
    void theTextsMatchThePipelineFile() throws IOException {
        Path python = Path.of("..", "pipeline", "src", "pipeline", "media_ai.py");
        assumeThat(Files.exists(python)).isTrue();
        String file = Files.readString(python, StandardCharsets.UTF_8);

        assertThat(file).contains("PROMPT_VERSION = \"" + AiPrompts.VERSION + "\"");
        // STYLE and KID are Python string literals (STYLE has several pieces): join the pieces.
        assertThat(pythonStrings(file, "STYLE = (", "\n)")).isEqualTo(AiPrompts.STYLE);
        assertThat(pythonStrings(file, "KID = ", "\n")).isEqualTo(AiPrompts.KID);
        // SCENE_SYSTEM and the Codex wrapper: every line is in the Python text. In an f-string the braces are doubled
        // and {KID} stands for the kid.
        for (String line : AiPrompts.SCENE_SYSTEM.split("\n")) {
            String expected = line.replace(AiPrompts.KID, "\u0000").replace("{", "{{").replace("}", "}}")
                    .replace("\u0000", "{KID}");
            assertThat(file).as("line of SCENE_SYSTEM").contains(expected);
        }
        for (String line : AiPrompts.CODEX_WRAPPER.split("\n")) {
            assertThat(file).as("line of the Codex wrapper").contains(line.replace("{", "{{").replace("}", "}}"));
        }
    }

    /** The string literals between {@code start} and {@code end} in the Python file, joined. */
    private static String pythonStrings(String file, String start, String end) {
        int from = file.indexOf(start) + start.length();
        String block = file.substring(from, file.indexOf(end, from));
        Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(block);
        StringBuilder joined = new StringBuilder();
        while (literal.find()) {
            joined.append(literal.group(1));
        }
        return joined.toString();
    }
}
