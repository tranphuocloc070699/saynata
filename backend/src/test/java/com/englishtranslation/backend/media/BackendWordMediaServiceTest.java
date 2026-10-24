package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import com.englishtranslation.backend.word.WordResponse;
import com.englishtranslation.backend.word.WordService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The background photo and clip job for new backend words, with a fake Pixabay and a fake converter. */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.media.pixabay-api-key=test-pixabay-key"})
class BackendWordMediaServiceTest {

    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) throws IOException {
        mediaDir = Files.createTempDirectory("saynata-media-test");
        registry.add("app.media.dir", () -> mediaDir.toString());
    }

    @Autowired
    BackendWordMediaService mediaService;
    @Autowired
    WordService wordService;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    SenseMediaReader reader;
    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    PixabayClient pixabay;
    @MockitoBean
    MediaConverter converter;
    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void clean() throws IOException {
        jdbc.sql("delete from word_lookup_media").update();
        jdbc.sql("update dictionary_sense set media_id = null").update();
        jdbc.sql("delete from media").update();
        lookups.deleteAll();
        try (var files = Files.list(mediaDir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        // The converter writes a tiny fake file where it is told to.
        doAnswer(call -> {
            Files.write(call.getArgument(1), new byte[] {1, 2, 3, 4});
            return null;
        }).when(converter).toPhoto(any(), any());
        doAnswer(call -> {
            Files.write(call.getArgument(1), new byte[] {9, 8, 7, 6, 5});
            return null;
        }).when(converter).toClip(any(), any());
        when(pixabay.download(anyString())).thenReturn(new byte[] {42});
    }

    private static WordEntry entryWith(String... parts) {
        List<WordEntry.Sense> senses = new java.util.ArrayList<>();
        for (String pos : parts) {
            senses.add(new WordEntry.Sense(pos, "B1", "not wanting to do something", "nghĩa",
                    List.of(new WordEntry.Example("easy", "She was reluctant to leave.")), List.of()));
        }
        return new WordEntry(senses);
    }

    private long saveLookup(String word, WordEntry entry) {
        WordLookup row = new WordLookup("key-" + word, word, "{\"senses\":[]}", "test-model", "v7");
        return lookups.save(row).getId();
    }

    private static PixabayHit hit(String kind, long id) {
        return new PixabayHit(kind, id, "https://cdn.pixabay.com/x/" + id, "Jane Photo", "https://pixabay.com/photos/x-" + id + "/");
    }

    @Test
    void aNounGetsAPhotoFileAndAMediaRowWithTheCredit() {
        WordEntry entry = entryWith("noun");
        long id = saveLookup("reluctance", entry);
        when(pixabay.findPhoto("reluctance")).thenReturn(Optional.of(hit("photo", 777)));

        mediaService.fetch(id, "reluctance", entry);

        Map<Integer, SenseMedia> media = reader.forBackendWords(List.of(id)).get(id);
        assertThat(media).containsOnlyKeys(0);
        SenseMedia saved = media.get(0);
        assertThat(saved.kind()).isEqualTo("photo");
        assertThat(saved.photographer()).isEqualTo("Jane Photo");
        assertThat(saved.pageUrl()).isEqualTo("https://pixabay.com/photos/x-777/");
        assertThat(saved.url()).startsWith("https://api.saynata.com/media/reluctance_1_").endsWith(".webp");
        String fileName = saved.url().substring(saved.url().lastIndexOf('/') + 1);
        assertThat(mediaDir.resolve(fileName)).exists().hasBinaryContent(new byte[] {1, 2, 3, 4});
        assertThat(jdbc.sql("select pixabay_id from media where file_name = :f").param("f", fileName)
                .query(Long.class).single()).isEqualTo(777L);
        // No half-written files are left behind.
        assertThat(mediaDir.toFile().list()).containsExactly(fileName);
    }

    @Test
    void aVerbGetsAClip() {
        WordEntry entry = entryWith("verb");
        long id = saveLookup("sprint", entry);
        when(pixabay.findClip("sprint")).thenReturn(Optional.of(hit("clip", 5)));

        mediaService.fetch(id, "sprint", entry);

        SenseMedia saved = reader.forBackendWords(List.of(id)).get(id).get(0);
        assertThat(saved.kind()).isEqualTo("clip");
        assertThat(saved.url()).endsWith(".mp4");
        verify(converter).toClip(any(), any());
        verify(pixabay, never()).findPhoto(anyString());
    }

    @Test
    void aVerbWithoutAClipFallsBackToAPhoto() {
        WordEntry entry = entryWith("verb");
        long id = saveLookup("sprint", entry);
        when(pixabay.findClip("sprint")).thenReturn(Optional.empty());
        when(pixabay.findPhoto("sprint")).thenReturn(Optional.of(hit("photo", 6)));

        mediaService.fetch(id, "sprint", entry);

        assertThat(reader.forBackendWords(List.of(id)).get(id).get(0).kind()).isEqualTo("photo");
    }

    @Test
    void sensesThatAreNotNounsAdjectivesOrVerbsAreSkipped() {
        WordEntry entry = entryWith("adverb", "preposition");
        long id = saveLookup("quickly", entry);

        mediaService.fetch(id, "quickly", entry);

        verify(pixabay, never()).findPhoto(anyString());
        verify(pixabay, never()).findClip(anyString());
        assertThat(jdbc.sql("select count(*) from word_lookup_media").query(Long.class).single()).isZero();
    }

    @Test
    void aFailureIsNotThrownAndPixabayIsNotAskedAgainForTheSameSense() {
        WordEntry entry = entryWith("noun");
        long id = saveLookup("reluctance", entry);
        when(pixabay.findPhoto("reluctance")).thenThrow(new IllegalStateException("Pixabay search answered 500"));

        mediaService.fetch(id, "reluctance", entry);   // must not throw
        mediaService.fetch(id, "reluctance", entry);   // the sense is already claimed

        verify(pixabay, times(1)).findPhoto("reluctance");
        assertThat(reader.forBackendWords(List.of(id))).isEmpty();
        assertThat(jdbc.sql("select count(*) from word_lookup_media where media_id is null").query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    void aFailedConversionLeavesNoFileAndNoMediaRow() {
        WordEntry entry = entryWith("noun");
        long id = saveLookup("reluctance", entry);
        when(pixabay.findPhoto("reluctance")).thenReturn(Optional.of(hit("photo", 1)));
        doAnswer(call -> {
            throw new IllegalStateException("ffmpeg failed with exit code 1");
        }).when(converter).toPhoto(any(), any());

        mediaService.fetch(id, "reluctance", entry);

        assertThat(mediaDir.toFile().list()).isEmpty();
        assertThat(jdbc.sql("select count(*) from media").query(Long.class).single()).isZero();
    }

    @Test
    void aSuccessfulSenseIsNotAskedAgainEither() {
        WordEntry entry = entryWith("noun");
        long id = saveLookup("reluctance", entry);
        when(pixabay.findPhoto("reluctance")).thenReturn(Optional.of(hit("photo", 1)));

        mediaService.fetch(id, "reluctance", entry);
        mediaService.fetch(id, "reluctance", entry);

        verify(pixabay, times(1)).findPhoto("reluctance");
        assertThat(jdbc.sql("select count(*) from media").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void aNewWordIsAnsweredFirstAndTheImageUrlComesOnTheNextLookup() throws Exception {
        WordEntry entry = entryWith("noun");
        when(claude.describeWord("reluctant")).thenReturn(entry);
        when(pixabay.findPhoto("reluctant")).thenReturn(Optional.of(hit("photo", 321)));

        WordResponse first = wordService.lookUp("reluctant");

        assertThat(first.cached()).isFalse();
        assertThat(first.entry().senses().get(0).imageUrl()).isNull();

        String imageUrl = null;
        for (int i = 0; i < 100 && imageUrl == null; i++) {
            Thread.sleep(50);
            imageUrl = wordService.lookUp("reluctant").entry().senses().get(0).imageUrl();
        }
        assertThat(imageUrl).startsWith("https://api.saynata.com/media/reluctant_1_").endsWith(".webp");
    }

    @Test
    void theWordIsAnsweredEvenWhenPixabayIsBroken() throws Exception {
        WordEntry entry = entryWith("noun");
        when(claude.describeWord("reluctant")).thenReturn(entry);
        when(pixabay.findPhoto(anyString())).thenThrow(new IllegalStateException("network down"));

        WordResponse first = wordService.lookUp("reluctant");
        Thread.sleep(300);
        WordResponse second = wordService.lookUp("reluctant");

        assertThat(first.entry().senses()).hasSize(1);
        assertThat(second.cached()).isTrue();
        assertThat(second.entry().senses().get(0).imageUrl()).isNull();
    }
}
