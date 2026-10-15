//
//  SentenceWordsSection.swift
//  EnglishTranslation
//

import SwiftUI

/// The "Words" heading and the compact word cards of a sentence. Tapping a single word opens its full word card.
/// Used by the full result and by the loading screens, so the cards look the same while the answer is coming (TASK-74).
struct SentenceWordsSection: View {
    let words: [SentenceWordInfo]
    let language: TargetLanguage
    /// Gets the full word card for a tapped word. The real one is `TranslationStore.wordEntry(for:)`.
    let loadWordEntry: (SentenceWordInfo) async throws -> WordEntry

    @State private var openedWord: SentenceWordInfo?

    var body: some View {
        if !words.isEmpty {
            VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
                Text("Words")
                    .font(.headline)
                    .foregroundStyle(Theme.Colors.textPrimary)
                ForEach(words) { info in
                    SentenceWordCard(info: info) { openedWord = info }
                }
            }
            .sheet(item: $openedWord) { info in
                SentenceWordSheet(info: info, targetLanguage: language, load: loadWordEntry)
            }
        }
    }
}
