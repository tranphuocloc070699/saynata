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

/// The part of the `GET /api/words/{word}` reply we need: `{ word, entry: { senses }, cached }`.
private struct WordResponse: Decodable {
    let word: String
    let entry: Body

    struct Body: Decodable {
        let senses: [Sense]
    }

    /// The backend sends no IPA, so the card shows none.
    var wordEntry: WordEntry {
        WordEntry(word: word, ipa: nil, senses: entry.senses)
    }
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
/// so the app needs no API key.
struct BackendClient: Translating, WordFetching {
    /// The API root, for example `http://localhost:8081`.
    let baseURL: URL
    /// Tests pass a session with a fake `URLProtocol`.
    var session: URLSession = .shared
    /// Answers we already got. A repeat request with the same key never reaches the network. Nil means no cache.
    var cache: ResultCache?

    /// Returns the explanation from the local cache if it is there; otherwise asks the backend and saves
    /// the answer. Throws `BackendError` or a `URLError`.
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        let key = CacheKeys.sentenceKey(text, targetLang: language.code)
        if let saved = await cache?.data(forKey: key),
           let response = try? JSONDecoder().decode(TranslationResponse.self, from: saved) {
            return response.explanation
        }

        let (data, explanation) = try await askBackend(text, translatingTo: language)
        await cache?.save(data, forKey: key, kind: .sentence)
        return explanation
    }

    /// Sends `text` to the backend. Returns the raw reply (to cache as-is) and the decoded explanation.
    private func askBackend(_ text: String, translatingTo language: TargetLanguage) async throws -> (Data, Explanation) {
        var request = URLRequest(url: baseURL.appending(path: "api/translations"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(TranslationRequest(text: text, targetLang: language.code))

        let data = try await sendChecked(request)
        return (data, try JSONDecoder().decode(TranslationResponse.self, from: data).explanation)
    }

    /// Returns the entry from the local cache if it is there; otherwise asks `GET /api/words/{word}`
    /// and saves the answer. The word is normalised first, so "Run" and " run " share one cache entry.
    func entry(forWord word: String) async throws -> WordEntry {
        let key = CacheKeys.wordKey(word)
        if let saved = await cache?.data(forKey: key),
           let response = try? JSONDecoder().decode(WordResponse.self, from: saved) {
            return response.wordEntry
        }

        // `appending(component:)` encodes the word, so a "/" in it cannot change the path.
        let url = baseURL.appending(path: "api/words").appending(component: CacheKeys.normalizeWord(word))
        let data = try await sendChecked(URLRequest(url: url))
        let response = try JSONDecoder().decode(WordResponse.self, from: data)
        await cache?.save(data, forKey: key, kind: .word)
        return response.wordEntry
    }

    /// Sends the request. Returns the body of a 200 reply; any other status throws `BackendError.badStatus`.
    private func sendChecked(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)

        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard statusCode == 200 else {
            let detail = try? JSONDecoder().decode(ProblemDetail.self, from: data).detail
            throw BackendError.badStatus(statusCode, detail: detail)
        }
        return data
    }
}
