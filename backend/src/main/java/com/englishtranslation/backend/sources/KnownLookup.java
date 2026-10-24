package com.englishtranslation.backend.sources;

import java.util.Collection;
import java.util.Set;

/** Which words and phrases the app can already answer, or already has in the prefill queue. */
public interface KnownLookup {

    /** The words (lower case) that are bundled, a form of a bundled word, cached, or queued. */
    Set<String> knownWords(Collection<String> words);

    /** The phrases (lower case) that are cached (as a word card or a sentence) or queued. */
    Set<String> knownPhrases(Collection<String> phrases);
}
