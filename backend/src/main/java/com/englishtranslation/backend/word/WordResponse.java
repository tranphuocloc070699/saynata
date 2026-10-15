package com.englishtranslation.backend.word;

/**
 * The reply of GET /api/words/{word}. ipa is filled at request time from IpaDictionary, not saved
 * with the cached entry. It is null when the word is not on the IPA list.
 * Each sense of the entry may have an imageUrl (see WordEntryView).
 */
public record WordResponse(String word, WordEntryView entry, boolean cached, String ipa) {
}
