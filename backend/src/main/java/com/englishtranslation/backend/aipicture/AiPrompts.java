package com.englishtranslation.backend.aipicture;

import com.englishtranslation.backend.word.WordEntry;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts for the AI picture of a sense (TASK-91). KID, STYLE, SCENE_SYSTEM and the Codex wrapper are copied WORD
 * FOR WORD from pipeline/src/pipeline/media_ai.py. They must match the pipeline's {@code PROMPT_VERSION = "ai-v1"}:
 * change them only together with a new version there, so the admin pictures look like the pipeline pictures.
 * AiPromptsTest checks this against the Python file.
 */
public final class AiPrompts {

    /** The prompt version of the texts below (pipeline PROMPT_VERSION). Also app.claude.scene-prompt-version. */
    public static final String VERSION = "ai-v1";

    public static final String STYLE = "Style: cute cartoon vector illustration like a children's picture book, "
            + "bold clean outlines, flat bright "
            + "colours. People and animals have big expressive eyes and mouths; objects NEVER have faces, eyes, arms or legs. "
            + "Clothes in soft sage green and deep teal (#1F6B5F). Plain light grey-white background. Subject in the centre, "
            + "everything important inside the middle 16:9 area. No text, letters or numbers anywhere.";

    public static final String KID = "the kid (about 8 years old, short brown hair, sage green T-shirt, "
            + "deep teal jeans, white sneakers)";

    /** The methods Claude may choose. "none" means the sense cannot be drawn. */
    public static final Set<String> METHODS = Set.of("object", "need", "emotion", "action", "quality", "relation",
            "role", "story", "repetition", "none");

    /** The system prompt for the scene (media_ai.SCENE_SYSTEM, with {KID} filled in). */
    public static final String SCENE_SYSTEM = """
            You are the art director for a picture dictionary for English learners (B1-B2).
            For ONE sense of a word you write the scene an illustrator will draw. The picture must make a learner guess
            THIS sense of THIS word, and not a near word or another sense.

            When the meaning needs a person (a need, an emotion, an action, a story), the main character is always the same
            kid: {KID}. Write "the kid" in the scene. Do NOT add the kid or any person when the word is a thing or a place:
            show only the thing. People appear in a thing or place only when they are part of what it is (customers and a
            cashier at the counter inside a bank). Other people (an old man, a doctor) only when the word needs them.
            People have expressive faces. Objects never have faces.

            First pick the method:
            - object: concrete noun, thing or place. The thing alone, in its most typical form, nothing distracting,
              no kid. For a building whose meaning is what happens inside (bank, post office, hospital), show the building
              cut open or through a big window so the typical activity inside is visible. For a part of a place (river bank,
              roof), show the whole place and make that part the clear focus.
            - need: body need or state (thirsty, hungry, tired, cold, sick). The kid shows it clearly on face and body,
              with a thought bubble showing what he needs (water, food, a bed).
            - emotion: feeling (ashamed, proud, angry, afraid). The kid's face and body show it, and the cause is in the
              scene (a broken vase for ashamed). No thought bubble.
            - action: verb or behaviour (hello, carry, run). The kid in the middle of the action, with small motion lines;
              the object of the action visible.
            - quality: adjective. ONE typical person or thing that clearly has the quality (an old man with a walking stick
              for old, an empty transparent box for empty). If one subject alone could mean another word, add one small
              hint or use a contrast with the opposite faded.
            - relation: preposition or place word. Two simple, very familiar objects whose position shows the relation;
              exaggerate it.
            - role: person or job (doctor, champion). A person with the clothes and tools of the role, doing the job.
            - story: abstract word. One small, familiar moment where this word is the only natural description.
            - repetition: count, frequency or time words. Identical side-by-side panels, one per time, or the sun's
              position for times of day (no clocks).
            - none: the sense cannot be drawn so a learner would guess it (grammar words, very abstract senses).

            Rules:
            - One clear focus. Every element must help the meaning; remove the rest.
            - Never rely on text, numbers, symbols that need reading, or speech bubbles with words.
            - Avoid the other senses of the word (they are listed) and avoid things that would suggest the distractors.
            - No violence, no brands, no real people.
            - Distractors: 3 real English words a learner might guess from a weak picture of this sense
              (near synonyms, the opposite, the neighbouring idea). Not other senses of the same word.

            Answer with JSON only:
            {"method": "...", "scene": "2-4 short sentences: what is in the picture and where", "focus": "the one thing
            the eye must land on", "avoid": "what must NOT appear", "distractors": ["w1","w2","w3"]}"""
            .replace("{KID}", KID);

    /** What Codex gets before the image prompt (media_ai.codex_prompt, up to and including "IMAGE PROMPT:"). */
    static final String CODEX_WRAPPER = """
            Use your built-in image generation tool exactly ONCE with the IMAGE PROMPT below, word for word.
            Do not add, remove or reword anything. Landscape 3:2. One image only. Do not run any other command or edit files.
            Then reply with only this JSON: {"prompt_used": "<the exact prompt you sent>"}
            If the tool refuses or fails, reply {"error": "<reason>"}.

            IMAGE PROMPT:
            """;

    private static final Pattern THE_KID = Pattern.compile("\\bthe kid\\b", Pattern.CASE_INSENSITIVE);

    private AiPrompts() {
    }

    /** The whole text Codex receives: the wrapper, then the image prompt the admin edited. */
    public static String codexPrompt(String imagePrompt) {
        return CODEX_WRAPPER + imagePrompt;
    }

    /**
     * The image prompt of a scene: the kid text is added after the first "the kid", then the focus and the things that
     * must not appear, then the style (the part of media_ai.codex_prompt after "IMAGE PROMPT:").
     */
    public static String imagePrompt(String scene, String focus, String avoid) {
        String withKid = nullToEmpty(scene);
        Matcher firstKid = THE_KID.matcher(withKid);
        if (firstKid.find()) {
            // KID.substring(8) is the text after "the kid ": the description in brackets.
            withKid = withKid.substring(0, firstKid.end()) + " " + KID.substring(8) + withKid.substring(firstKid.end());
        }
        return withKid + " Main focus: " + nullToEmpty(focus) + ". Must not appear: " + nullToEmpty(avoid) + ".\n" + STYLE;
    }

    /**
     * The prompt for a sense that has no media-ai scene yet (media_generate.build_prompt), but with the media-ai
     * STYLE at the end, so the picture looks like the others.
     */
    public static String template(String word, WordEntry.Sense sense) {
        String example = easyExample(sense);
        StringBuilder text = new StringBuilder();
        text.append("A picture that shows the meaning of the English word \"").append(word).append("\" (")
                .append(nullToEmpty(sense.pos())).append(") for a language learner.\n");
        text.append("Meaning: ").append(nullToEmpty(sense.simpleDefinition())).append('\n');
        if (!example.isEmpty()) {
            text.append("Example sentence (do not write it in the picture): \"").append(example).append("\"\n");
        }
        text.append("Show this meaning clearly, so a learner can guess the word from the picture alone.\n");
        text.append(STYLE);
        return text.toString();
    }

    /** The user message for the scene call (media_ai.scene_message, without feedback). */
    public static String sceneMessage(String word, WordEntry.Sense sense, List<WordEntry.Sense> otherSenses) {
        StringBuilder others = new StringBuilder("[");
        for (int i = 0; i < otherSenses.size(); i++) {
            if (i > 0) {
                others.append(", ");
            }
            WordEntry.Sense other = otherSenses.get(i);
            others.append("{\"pos\": ").append(jsonString(other.pos())).append(", \"meaning\": ")
                    .append(jsonString(other.simpleDefinition())).append('}');
        }
        others.append(']');
        return "Word: \"" + word + "\" (" + nullToEmpty(sense.pos()) + ")\nThis sense: "
                + nullToEmpty(sense.simpleDefinition()) + "\nExample: " + easyExample(sense)
                + "\nOther senses of the word (do NOT show these): " + others;
    }

    /** pipeline.media_check.easy_example: the example with level "easy", else the first one, else empty. */
    static String easyExample(WordEntry.Sense sense) {
        List<WordEntry.Example> examples = sense.examples();
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        for (WordEntry.Example example : examples) {
            if (example != null && "easy".equals(example.level())) {
                return nullToEmpty(example.text());
            }
        }
        return examples.get(0) == null ? "" : nullToEmpty(examples.get(0).text());
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    /** A JSON string like Python's json.dumps: quotes, backslashes, control characters and non-ASCII are escaped. */
    private static String jsonString(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : nullToEmpty(text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
