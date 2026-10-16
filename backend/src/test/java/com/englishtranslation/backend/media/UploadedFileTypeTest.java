package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** TASK-79: the type of an upload comes from its first bytes. */
class UploadedFileTypeTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[64];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(int offset, String text) {
        byte[] result = new byte[64];
        byte[] part = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(part, 0, result, offset, part.length);
        return result;
    }

    @Test
    void picturesAreRecognised() {
        assertThat(UploadedFileType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("photo"); // JPEG
        assertThat(UploadedFileType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo("photo");
        assertThat(UploadedFileType.detect(ascii(0, "GIF89a"))).isEqualTo("photo");
        byte[] webp = ascii(0, "RIFF");
        System.arraycopy("WEBP".getBytes(StandardCharsets.ISO_8859_1), 0, webp, 8, 4);
        assertThat(UploadedFileType.detect(webp)).isEqualTo("photo");
        byte[] heic = ascii(4, "ftypheic");
        assertThat(UploadedFileType.detect(heic)).isEqualTo("photo");
    }

    @Test
    void clipsAreRecognised() {
        assertThat(UploadedFileType.detect(ascii(4, "ftypisom"))).isEqualTo("clip"); // MP4
        assertThat(UploadedFileType.detect(ascii(4, "ftypqt  "))).isEqualTo("clip"); // MOV
        assertThat(UploadedFileType.detect(ascii(4, "moov"))).isEqualTo("clip");     // old MOV
        assertThat(UploadedFileType.detect(bytes(0x1A, 0x45, 0xDF, 0xA3))).isEqualTo("clip"); // WebM
    }

    @Test
    void everythingElseIsRefused() {
        assertThat(UploadedFileType.detect("just some text, long enough".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(UploadedFileType.detect(ascii(0, "%PDF-1.7"))).isNull();
        assertThat(UploadedFileType.detect(ascii(0, "<svg xmlns=\"http://www.w3.org/2000/svg\">"))).isNull();
        assertThat(UploadedFileType.detect(new byte[] {1, 2, 3})).isNull();
        assertThat(UploadedFileType.detect(null)).isNull();
    }
}
