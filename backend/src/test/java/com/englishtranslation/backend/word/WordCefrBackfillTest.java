package com.englishtranslation.backend.word;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import com.englishtranslation.backend.claude.ClaudeService;

/** Saved words from before migration V6 have no level; start-up fills it from the entry JSON. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
class WordCefrBackfillTest {

    @Autowired
    WordLookupRepository repository;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void fillsTheLevelOfTheFirstSenseAndSkipsBrokenRows() {
        String json = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"B2\",\"simpleDefinition\":\"d\",\"vietnamese\":\"v\","
                + "\"examples\":[],\"usage\":[]},{\"pos\":\"verb\",\"cefr\":\"A1\",\"simpleDefinition\":\"d\","
                + "\"vietnamese\":\"v\",\"examples\":[],\"usage\":[]}]}";
        WordLookup good = repository.save(new WordLookup("key-good", "good", json, "m", "v7"));
        WordLookup broken = repository.save(new WordLookup("key-broken", "broken", "not json", "m", "v7"));

        new WordCefrBackfill(repository, jsonMapper).run(null);

        assertThat(repository.findById(good.getId()).orElseThrow().getCefr()).isEqualTo("B2");
        assertThat(repository.findById(broken.getId()).orElseThrow().getCefr()).isNull();
    }
}
