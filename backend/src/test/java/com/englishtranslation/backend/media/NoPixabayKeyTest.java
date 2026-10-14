package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** PIXABAY_API_KEY empty (the default): the job does nothing, quietly. */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.media.pixabay-api-key="})
class NoPixabayKeyTest {

    @Autowired
    BackendWordMediaService mediaService;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    PixabayClient pixabay;

    @Test
    void withoutAKeyPixabayIsNeverCalledAndNothingIsClaimed() {
        WordLookup row = lookups.save(new WordLookup("key-nokey", "nokey", "{\"senses\":[]}", "test-model", "v7"));
        WordEntry entry = new WordEntry(List.of(new WordEntry.Sense("noun", "B1", "a meaning", "nghĩa",
                List.of(new WordEntry.Example("easy", "An example.")), List.of())));

        mediaService.fetch(row.getId(), "nokey", entry);

        verifyNoInteractions(pixabay);
        assertThat(jdbc.sql("select count(*) from word_lookup_media").query(Long.class).single()).isZero();
        lookups.deleteAll();
    }
}
