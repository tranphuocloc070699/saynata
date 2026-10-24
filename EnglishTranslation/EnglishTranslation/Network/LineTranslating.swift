//
//  LineTranslating.swift
//  EnglishTranslation
//

import Foundation

/// Asks the backend for the target-language line of short English texts (TASK-106.3, `POST /api/translate-lines`).
/// A fake in the tests stands in for the backend.
protocol LineTranslating: Sendable {
    /// Returns one entry per text, in the same order. Nil means the backend has no line for that text yet
    /// (a guest only gets saved lines). Throws `BackendError` or a `URLError` when the call fails.
    func lines(for texts: [String], in language: TargetLanguage) async throws -> [String?]
}

/// The body of `POST /api/translate-lines`.
private struct LinesRequest: Encodable {
    let targetLang: String
    let texts: [String]
}

/// The reply: `{ targetLang, lines: [{ text, translation, cached }] }`, in the order of the request.
private struct LinesResponse: Decodable {
    let lines: [Line]

    struct Line: Decodable {
        let translation: String?
    }
}

extension BackendClient: LineTranslating {
    /// The backend takes 1-20 texts of 1-500 characters, so longer lists are sent in groups of 20.
    static let maxLinesPerRequest = 20

    /// Signed in: a normal call (a miss is written by the LLM and saved for everyone). Guest: `cachedOnly=true`,
    /// so the server answers only from its saved lines and never calls Claude (doc-5).
    func lines(for texts: [String], in language: TargetLanguage) async throws -> [String?] {
        let isGuest = await auth?.accessToken() == nil
        var result: [String?] = []
        for start in stride(from: 0, to: texts.count, by: Self.maxLinesPerRequest) {
            let group = Array(texts[start..<min(start + Self.maxLinesPerRequest, texts.count)])
            result += try await linesGroup(group, in: language, cachedOnly: isGuest)
        }
        return result
    }

    private func linesGroup(_ texts: [String], in language: TargetLanguage, cachedOnly: Bool) async throws -> [String?] {
        var url = baseURL.appending(path: "api/translate-lines")
        if cachedOnly {
            url = url.appending(queryItems: [URLQueryItem(name: "cachedOnly", value: "true")])
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(LinesRequest(targetLang: language.code, texts: texts))

        let data = try await sendChecked(request)
        let lines = try JSONDecoder().decode(LinesResponse.self, from: data).lines
        // A reply of another length cannot be matched to the texts, so no line is used.
        guard lines.count == texts.count else { return Array(repeating: nil, count: texts.count) }
        return lines.map { line in
            line.translation.flatMap { $0.isEmpty ? nil : $0 }
        }
    }
}
