//
//  SentenceMeaningView.swift
//  EnglishTranslation
//

import SwiftUI

/// A sentence result while the answer is still coming (TASK-65, TASK-73, TASK-74, TASK-89.2). It is the same
/// `SentenceCard` as `SentenceResultView`, so nothing jumps when the full result replaces it:
/// - Before any text: the original sentence at once, and a spinner where the summary will be.
/// - While the summary grows: the text, word by word.
/// The target-language row is left out until the answer is complete.
struct SentenceMeaningView: View {
    /// The summary so far. An empty `meaning` means nothing has been written yet.
    let partial: SentenceMeaning
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    var body: some View {
        SentenceCard(
            sentence: partial.sentence,
            summary: partial.meaning,
            translation: nil,
            language: partial.language,
            loadWordEntry: loadWordEntry)
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
