package com.englishtranslation.backend.media;

import java.nio.file.Path;

/** Makes the small files we serve. The real one is FfmpegMediaConverter. */
public interface MediaConverter {

    /** Any image to a WebP photo, at most 320 px wide (same as the pipeline). Writes to {@code target}. */
    void toPhoto(byte[] original, Path target);

    /**
     * An AI picture (TASK-91) to a WebP: cropped to 3:2 around the middle, at most 1536 px wide, quality 80 (same as
     * prepare_images in the pipeline's media_ai.py). Writes to {@code target}.
     */
    void toAiPicture(byte[] original, Path target);

    /** Any video to a muted H.264 MP4 of at most 3 seconds, 320 px wide (same as the pipeline). */
    void toClip(byte[] original, Path target);
}
