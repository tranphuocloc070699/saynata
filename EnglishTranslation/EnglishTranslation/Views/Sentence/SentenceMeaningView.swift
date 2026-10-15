//
//  SentenceMeaningView.swift
//  EnglishTranslation
//

import SwiftUI

/// A sentence result while only the meaning has arrived (TASK-65). It has the same header and Meaning card as
/// `SentenceResultView`, so nothing jumps when the full result replaces it. A small spinner sits where the
/// Words will appear, and the target-language row is left out until the answer is complete.
struct SentenceMeaningView: View {
    let partial: SentenceMeaning

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            SentenceHeader(sentence: partial.sentence)

            MeaningCard(meaning: partial.meaning, translation: nil, language: partial.language)

            ProgressView()
                .frame(maxWidth: .infinity, alignment: .leading)
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
