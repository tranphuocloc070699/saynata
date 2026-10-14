package com.englishtranslation.backend.media;

/**
 * The picture or clip of one sense, with the credit Pixabay asks for.
 *
 * @param url          absolute address, for example https://api.loffy.me/media/run_1_3fa9c2d1.mp4
 * @param kind         "photo" or "clip"
 * @param photographer Pixabay user name
 * @param pageUrl      the Pixabay page of the file
 */
public record SenseMedia(String url, String kind, String photographer, String pageUrl, String licenseNote) {
}
