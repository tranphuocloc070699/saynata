//
//  Translating.swift
//  EnglishTranslation
//

import Foundation

/// Anything that can explain a sentence. `BackendClient` is the real one; tests use a fake.
protocol Translating: Sendable {
    /// Explains `text` in simple English, with a translation into `language`.
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation

    /// Same answer as `explain`, but `onMeaning` is called with the simple English meaning as soon as it is
    /// ready, before the words and the translation (TASK-65). It is not called when the answer comes from a cache.
    /// The returned explanation is the complete one.
    func explainStreaming(
        _ text: String,
        translatingTo language: TargetLanguage,
        onMeaning: @escaping @MainActor @Sendable (String) -> Void
    ) async throws -> Explanation
}

extension Translating {
    /// A translator that cannot stream just waits for the whole answer.
    func explainStreaming(
        _ text: String,
        translatingTo language: TargetLanguage,
        onMeaning: @escaping @MainActor @Sendable (String) -> Void
    ) async throws -> Explanation {
        try await explain(text, translatingTo: language)
    }
}
