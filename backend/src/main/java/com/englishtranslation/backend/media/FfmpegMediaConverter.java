package com.englishtranslation.backend.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Runs the ffmpeg program (installed in the backend Docker image). One tool for photos and clips. */
@Component
public class FfmpegMediaConverter implements MediaConverter {

    // 320 px wide, never bigger than the original, height rounded to an even number.
    private static final String SCALE = "scale=min(320\\,iw):-2";

    @Override
    public void toPhoto(byte[] original, Path target) {
        run(original, target, List.of("-frames:v", "1", "-vf", SCALE, "-c:v", "libwebp", "-quality", "80"));
    }

    @Override
    public void toClip(byte[] original, Path target) {
        run(original, target, List.of("-t", "3", "-an", "-vf", SCALE, "-c:v", "libx264", "-crf", "28",
                "-preset", "fast", "-pix_fmt", "yuv420p", "-movflags", "+faststart"));
    }

    private void run(byte[] original, Path target, List<String> options) {
        Path input = null;
        try {
            input = Files.createTempFile("pixabay-", ".bin");
            Files.write(input, original);
            List<String> command = new ArrayList<>(List.of("ffmpeg", "-y", "-loglevel", "error", "-i", input.toString()));
            command.addAll(options);
            command.add(target.toString());
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg took longer than 60 seconds");
            }
            if (process.exitValue() != 0 || !Files.exists(target) || Files.size(target) == 0) {
                // ffmpeg ran but could not make a file: the input is most likely broken.
                throw new UnreadableMediaException("ffmpeg failed with exit code " + process.exitValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not run ffmpeg: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running ffmpeg", e);
        } finally {
            if (input != null) {
                try {
                    Files.deleteIfExists(input);
                } catch (IOException ignored) {
                    // a temp file we could not delete is not worth failing for
                }
            }
        }
    }
}
