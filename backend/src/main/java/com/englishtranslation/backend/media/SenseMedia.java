package com.englishtranslation.backend.media;

/**
 * The picture or clip of one sense, with the credit Pixabay asks for.
 *
 * @param url          absolute address, for example https://api.loffy.me/media/run_1_3fa9c2d1.mp4
 * @param kind         "photo" or "clip"
 * @param photographer Pixabay user name
 * @param pageUrl      the Pixabay page of the file
 * @param source       "pixabay", "upload" when the admin uploaded the file (then photographer and pageUrl are null, TASK-79), or "ai" for an AI image from the pipeline (licenseNote names the model, TASK-53)
 * @param width        the file's size in pixels, so the app can size the frame before the file loads; null when not known (clips, most backend media)
 * @param height       the same, height
 */
public record SenseMedia(String url, String kind, String photographer, String pageUrl, String licenseNote, String source,
                         Integer width, Integer height) {
}
