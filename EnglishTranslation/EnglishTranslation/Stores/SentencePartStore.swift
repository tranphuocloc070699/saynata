//
//  SentencePartStore.swift
//  EnglishTranslation
//

import Foundation
import Observation
import SwiftUI

/// The answer for one selected part of a sentence, shown in the popup (TASK-89.2).
/// It asks the backend the same way as a full sentence (`POST /api/translations/stream`), so the summary
/// grows word by word and a part that was explained before comes from the local cache.
@Observable
@MainActor
final class SentencePartStore {
    /// Where the answer is.
    enum Phase: Equatable {
        /// Nothing written yet: the popup shows a spinner.
        case loading
        /// The summary so far (it grows while it streams).
        case summary(String)
        /// The full answer: the summary and the translation.
        case loaded(summary: String, translation: String)
        /// A message the user can read.
        case failed(String)

        /// The summary text, or "" before it starts.
        var summaryText: String {
            switch self {
            case .loading, .failed: ""
            case let .summary(text): text
            case let .loaded(summary, _): summary
            }
        }
    }

    private(set) var part = ""
    private(set) var language: TargetLanguage = .vietnamese
    private(set) var phase: Phase = .loading
    /// True once the full summary arrived. Late word-by-word pieces are then ignored (like TASK-73).
    private var summaryIsFinal = false

    private let makeTranslator: @MainActor () throws -> any Translating

    /// - Parameter makeTranslator: Builds the translator on each request, like `TranslationStore`.
    init(makeTranslator: @escaping @MainActor () throws -> any Translating = TranslatorFactory.makeTranslator) {
        self.makeTranslator = makeTranslator
    }

    /// Explains `part`. Call it from `.task(id:)`, so it stops when the popup closes.
    func explain(_ part: String, language: TargetLanguage) async {
        self.part = part
        self.language = language
        phase = .loading
        summaryIsFinal = false
        do {
            let translator = try makeTranslator()
            let explanation = try await translator.explainWithFallback(
                part,
                language: language,
                onMeaningDelta: { [weak self] piece in self?.append(piece) },
                onMeaning: { [weak self] summary in self?.show(summary) })
            summaryIsFinal = true
            phase = .loaded(summary: explanation.meaning, translation: explanation.translation)
        } catch is CancellationError {
            // The popup closed. Nothing to show.
        } catch let error as URLError where error.code == .cancelled {
            // Same: the request was cancelled on purpose.
        } catch {
            phase = .failed(error.localizedDescription)
        }
    }

    private func append(_ piece: String) {
        guard !summaryIsFinal else { return }
        phase = .summary(phase.summaryText + piece)
    }

    private func show(_ summary: String) {
        summaryIsFinal = true
        phase = .summary(summary)
    }
}

// MARK: - Environment

extension EnvironmentValues {
    /// Builds the store for a new popup. The app uses the real backend; previews and tests put in a fake.
    @Entry var makeSentencePartStore: @MainActor () -> SentencePartStore = { SentencePartStore() }
}
