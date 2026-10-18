package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real ffmpeg on tiny generated files. Skipped when ffmpeg is not installed. */
class FfmpegMediaConverterTest {

    @TempDir
    Path dir;

    private final FfmpegMediaConverter converter = new FfmpegMediaConverter();

    private static boolean ffmpegInstalled() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void aBigPhotoBecomesASmallWebp() throws IOException {
        assumeThat(ffmpegInstalled()).isTrue();
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().fillRect(0, 0, 300, 300);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        Path target = dir.resolve("photo.webp");

        converter.toPhoto(png.toByteArray(), target);

        byte[] webp = Files.readAllBytes(target);
        // A WebP file starts with "RIFF", 4 size bytes, then "WEBP".
        assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
    }

    /** The width and height of a picture file, read with ffprobe (null when ffprobe is not installed). */
    private static int[] sizeOf(Path file) throws Exception {
        try {
            Process process = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
                    "stream=width,height", "-of", "csv=p=0", file.toString()).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).strip();
            process.waitFor(30, TimeUnit.SECONDS);
            String[] parts = out.split(",");
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().fillRect(0, 0, width / 2, height / 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void anAiPictureIsCroppedToThreeByTwoAndAtMost1536Wide() throws Exception {
        assumeThat(ffmpegInstalled()).isTrue();
        // wide (too wide: sides cut), square (top and bottom cut), already 3:2 but big (scaled down), small 3:2 (kept)
        int[][] cases = {{2000, 1000, 1500, 1000}, {1000, 1000, 1000, 668}, {3000, 2000, 1536, 1024}, {600, 400, 600, 400}};
        for (int[] c : cases) {
            Path target = dir.resolve("ai-" + c[0] + "x" + c[1] + ".webp");

            converter.toAiPicture(png(c[0], c[1]), target);

            byte[] webp = Files.readAllBytes(target);
            assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(webp, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
            int[] size = sizeOf(target);
            if (size != null) {
                assertThat(size).as("size of " + c[0] + "x" + c[1]).containsExactly(c[2], c[3]);
            }
            // The size MediaStore writes in the media row follows the same rule.
            assertThat(MediaStore.aiSize(png(c[0], c[1]))).as("row size of " + c[0] + "x" + c[1]).containsExactly(c[2], c[3]);
        }
    }

    @Test
    void aVideoBecomesAShortMp4() throws Exception {
        assumeThat(ffmpegInstalled()).isTrue();
        Path source = dir.resolve("source.mp4");
        Process make = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "testsrc=duration=5:size=640x360:rate=10", "-c:v", "libx264", "-pix_fmt", "yuv420p", source.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectErrorStream(true).start();
        assumeThat(make.waitFor(60, TimeUnit.SECONDS) && make.exitValue() == 0).isTrue();
        Path target = dir.resolve("clip.mp4");

        converter.toClip(Files.readAllBytes(source), target);

        byte[] mp4 = Files.readAllBytes(target);
        // An MP4 file has "ftyp" at byte 4.
        assertThat(new String(mp4, 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp");
        assertThat((long) mp4.length).isLessThan(Files.size(source));
    }

    @Test
    void brokenInputFailsWithAClearMessage() {
        assumeThat(ffmpegInstalled()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> converter.toPhoto(new byte[] {1, 2, 3}, dir.resolve("x.webp")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ffmpeg failed");
    }
}
