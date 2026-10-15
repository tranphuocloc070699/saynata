//
//  SentenceMeaningView.swift
//  EnglishTranslation
//

import SwiftUI

/// A sentence result while the answer is still coming (TASK-65, TASK-73, TASK-74). It has the same header and
/// Meaning card as `SentenceResultView`, so nothing jumps when the full result replaces it.
/// - Before any text: an empty meaning, shown as a spinner inside the card.
/// - While the meaning grows, and after it: the text, then a small spinner for the rest of the answer.
/// The target-language row is left out until the answer is complete. The bundled dictionary words show below at
/// once; Claude's words are added above them when the full result arrives.
struct SentenceMeaningView: View {
    /// The meaning so far. An empty `meaning` means nothing has been written yet.
    let partial: SentenceMeaning
    /// The words found in the bundled dictionary.
    var localWords: [SentenceWordInfo] = []
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            SentenceHeader(sentence: partial.sentence)

            MeaningCard(meaning: partial.meaning, translation: nil, language: partial.language)

            if !partial.meaning.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            SentenceWordsSection(words: localWords, language: partial.language, loadWordEntry: loadWordEntry)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    SentenceMeaningView(
        partial: SentenceMeaning(
            sentence: "The mist blurred the edges of the world.",
            meaning: "The fog made the edges of everything hard to see clearly.",
            language: .vietnamese))
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}
