package com.englishtranslation.backend.media;

import java.nio.file.Path;

/** Makes the small files we serve. The real one is FfmpegMediaConverter. */
public interface MediaConverter {

    /** Any image to a WebP photo, at most 320 px wide (same as the pipeline). Writes to {@code target}. */
    void toPhoto(byte[] original, Path target);

    /** Any video to a muted H.264 MP4 of at most 3 seconds, 320 px wide (same as the pipeline). */
    void toClip(byte[] original, Path target);
}
