package com.englishtranslation.backend.media;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Finds out what an uploaded file really is from its first bytes ("magic bytes"), not from its name or the
 * Content-Type header, which the browser (or an attacker) can set to anything (TASK-79).
 * The answer is "photo" (JPEG, PNG, WebP, GIF, HEIC), "clip" (MP4, MOV, WebM) or null (not a picture or clip).
 */
public final class UploadedFileType {

    // ISO base media "brands" (bytes 8-11 after "ftyp") that mean a still picture, not a video.
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1");
    // A MOV file may start with one of these boxes instead of "ftyp".
    private static final Set<String> MOV_BOXES = Set.of("moov", "mdat", "free", "wide", "skip");

    private UploadedFileType() {
    }

    /** "photo", "clip" or null. */
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "photo"; // JPEG
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "photo"; // PNG
        }
        String start = ascii(bytes, 0, 6);
        if (start.equals("GIF87a") || start.equals("GIF89a")) {
            return "photo"; // GIF: only the first frame is used
        }
        if (ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return "photo";
        }
        if (startsWith(bytes, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "clip"; // WebM (Matroska)
        }
        String box = ascii(bytes, 4, 4);
        if (box.equals("ftyp")) {
            return HEIC_BRANDS.contains(ascii(bytes, 8, 4)) ? "photo" : "clip"; // HEIC picture, else MP4 or MOV
        }
        if (MOV_BOXES.contains(box)) {
            return "clip"; // older QuickTime MOV without ftyp
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int from, int length) {
        return new String(bytes, from, length, StandardCharsets.ISO_8859_1);
    }
}
