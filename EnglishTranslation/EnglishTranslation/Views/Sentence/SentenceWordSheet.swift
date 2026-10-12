//
//  SentenceWordSheet.swift
//  EnglishTranslation
//

import SwiftUI

/// The sheet that opens when the user taps a word card in a sentence result: the full word card.
/// A bundled word shows at once. Any other word is fetched first (cache, then backend).
struct SentenceWordSheet: View {
    let info: SentenceWordInfo
    let targetLanguage: TargetLanguage
    /// Gets the entry. The real one is `TranslationStore.wordEntry(for:)`.
    let load: (SentenceWordInfo) async throws -> WordEntry

    private enum Phase {
        case loading
        case loaded(WordEntry)
        case failed(String)
    }

    @State private var phase: Phase = .loading

    var body: some View {
        ScrollView {
            switch phase {
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.top, Theme.Spacing.xLarge)
            case let .loaded(entry):
                WordCardView(entry: entry, targetLanguage: targetLanguage)
                    .padding()
            case let .failed(message):
                Text(message)
                    .foregroundStyle(Theme.Colors.error)
                    .padding()
            }
        }
        .background(Theme.Colors.background)
        .task {
            do {
                phase = .loaded(try await load(info))
            } catch {
                phase = .failed(error.localizedDescription)
            }
        }
    }
}
