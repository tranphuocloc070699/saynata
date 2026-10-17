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

    @Environment(SpeechPlayer.self) private var speechPlayer
    @Environment(\.dismiss) private var dismiss
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
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
        }
        .background(Theme.Colors.background)
        // Option A (developer's choice, 2026-10-15): a grabber and a round close button at the top right.
        // The close button stays in place while the card scrolls. Swiping down still closes the sheet.
        .safeAreaInset(edge: .top, spacing: 0) {
            HStack {
                Spacer()
                IconButton(systemImage: "xmark", label: "Close", size: .compact) { dismiss() }
            }
            .padding(.horizontal, Theme.Spacing.small)
            .padding(.top, Theme.Spacing.small)
            .background(Theme.Colors.background)
        }
        .presentationDragIndicator(.visible)
        // The word card in this sheet has speakers. Closing the sheet stops them (TASK-46).
        .onDisappear { speechPlayer.stop() }
        .task {
            do {
                phase = .loaded(try await load(info))
            } catch {
                phase = .failed(error.localizedDescription)
            }
        }
    }
}
