//
//  BackendClient.swift
//  EnglishTranslation
//

import Foundation

/// The body of `POST /api/translations`.
private struct TranslationRequest: Encodable {
    let text: String
    let targetLang: String
}

/// The part of the backend reply we need.
private struct TranslationResponse: Decodable {
    let explanation: Explanation
}

/// The error body Spring Boot sends. Only `detail` is useful to show.
private struct ProblemDetail: Decodable {
    let detail: String?
}

/// What can go wrong when talking to our own backend.
enum BackendError: LocalizedError, Equatable {
    case badStatus(Int, detail: String?)

    var errorDescription: String? {
        switch self {
        case let .badStatus(code, detail: detail?): "\(detail) (status \(code))"
        case let .badStatus(code, detail: nil): "The server answered with status \(code)."
        }
    }
}

/// Talks to the Java backend in `backend/`. The backend calls Claude and caches every answer,
/// so the app needs no API key for this provider.
struct BackendClient: Translating {
    /// The API root, for example `http://localhost:8081`.
    let baseURL: URL
    /// Tests pass a session with a fake `URLProtocol`.
    var session: URLSession = .shared

    /// Sends `text` to the backend and returns its explanation. Throws `BackendError` or a `URLError`.
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        var request = URLRequest(url: baseURL.appending(path: "api/translations"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(TranslationRequest(text: text, targetLang: language.code))

        let (data, response) = try await session.data(for: request)

        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard statusCode == 200 else {
            let detail = try? JSONDecoder().decode(ProblemDetail.self, from: data).detail
            throw BackendError.badStatus(statusCode, detail: detail)
        }
        return try JSONDecoder().decode(TranslationResponse.self, from: data).explanation
    }
}
