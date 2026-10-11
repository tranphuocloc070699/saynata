package com.englishtranslation.backend.word;

/**
 * The reply of GET /api/words/{word}. ipa is filled at request time from IpaDictionary, not saved
 * with the cached entry. It is null when the word is not on the IPA list.
 */
public record WordResponse(String word, WordEntry entry, boolean cached, String ipa) {
}
