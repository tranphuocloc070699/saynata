package com.englishtranslation.backend.word;

import com.englishtranslation.backend.media.SenseMedia;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A WordEntry as GET /api/words/{word} sends it: the same fields, plus imageUrl on a sense that has media.
 * WordEntry itself stays as it is, because Claude writes it and the database stores it (no image there).
 * imageUrl is left out of the JSON when the sense has no media, so old app versions see nothing new.
 * imageWidth and imageHeight (pixels) come with it when the size is known, so the app sizes the frame before it loads.
 */
public record WordEntryView(List<SenseView> senses) {

    /** @param media sense position (0 = first sense) to its media; senses missing from the map have none */
    public static WordEntryView of(WordEntry entry, Map<Integer, SenseMedia> media) {
        List<SenseView> senses = new ArrayList<>();
        for (int i = 0; i < entry.senses().size(); i++) {
            WordEntry.Sense sense = entry.senses().get(i);
            SenseMedia senseMedia = media.get(i);
            senses.add(new SenseView(sense.pos(), sense.cefr(), sense.simpleDefinition(), sense.vietnamese(),
                    sense.examples(), sense.usage(), senseMedia == null ? null : senseMedia.url(),
                    senseMedia == null ? null : senseMedia.width(), senseMedia == null ? null : senseMedia.height()));
        }
        return new WordEntryView(senses);
    }

    public record SenseView(
            String pos,
            String cefr,
            String simpleDefinition,
            String vietnamese,
            List<WordEntry.Example> examples,
            List<WordEntry.Usage> usage,
            @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrl,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer imageWidth,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer imageHeight) {
    }
}
