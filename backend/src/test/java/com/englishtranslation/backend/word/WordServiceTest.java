package com.englishtranslation.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.claude.ClaudeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.claude.api-key=test-key")
class WordServiceTest {

    @Autowired
    WordService service;

    @Autowired
    WordLookupRepository repository;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void secondLookupComesFromCacheWithoutCallingClaude() {
        when(claude.describeWord("reluctant")).thenReturn(new WordEntry(List.of()));

        WordResponse first = service.lookUp("reluctant");
        WordResponse second = service.lookUp("  Reluctant ");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        verify(claude, times(1)).describeWord("reluctant");
    }

    @Test
    void savedEntryIsReadBackTheSame() {
        WordEntry entry = new WordEntry(List.of(new WordEntry.Sense(
                "adjective", "C1", "not wanting to do something", "miễn cưỡng",
                List.of(new WordEntry.Example("easy", "She was reluctant to leave."),
                        new WordEntry.Example("medium", "He gave a reluctant smile."),
                        new WordEntry.Example("harder", "They were reluctant to accept the offer.")),
                List.of(new WordEntry.Usage("pattern", "be reluctant to + verb", "She was reluctant to leave.")))));
        when(claude.describeWord("reluctant")).thenReturn(entry);

        WordResponse first = service.lookUp("Reluctant");
        WordResponse second = service.lookUp("reluctant");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.entry()).isEqualTo(entry);
        verify(claude, times(1)).describeWord("reluctant");
    }
}
