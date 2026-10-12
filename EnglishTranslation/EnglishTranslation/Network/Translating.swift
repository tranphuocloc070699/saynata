//
//  Translating.swift
//  EnglishTranslation
//

import Foundation

/// Anything that can explain a sentence. `BackendClient` is the real one; tests use a fake.
protocol Translating: Sendable {
    /// Explains `text` in simple English, with a translation into `language`.
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation
}
