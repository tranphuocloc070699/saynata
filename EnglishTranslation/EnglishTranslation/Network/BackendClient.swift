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

/// The part of the `GET /api/words/{word}` reply we need: `{ word, entry: { senses }, cached, ipa }`.
private struct WordResponse: Decodable {
    let word: String
    let entry: Body
    /// The backend adds it from an IPA list. Missing or null (older replies, rare words) means no IPA line.
    let ipa: String?

    struct Body: Decodable {
        let senses: [Sense]
    }

    var wordEntry: WordEntry {
        WordEntry(word: word, ipa: ipa, senses: entry.senses)
    }
}

/// The error body Spring Boot sends. Only `detail` is useful to show.
private struct ProblemDetail: Decodable {
    let detail: String?
}

/// The `meaning` event of `POST /api/translations/stream`.
private struct MeaningEvent: Decodable {
    let meaning: String
}

/// The `error` event of `POST /api/translations/stream`. `status` is the HTTP status the old endpoint would use.
private struct StreamErrorEvent: Decodable {
    let status: Int?
    let message: String?
}

/// What can go wrong when talking to our own backend.
enum BackendError: LocalizedError, Equatable {
    case badStatus(Int, detail: String?)
    /// The stream closed before the `done` event.
    case streamEnded

    var errorDescription: String? {
        switch self {
        case let .badStatus(code, detail: detail?): "\(detail) (status \(code))"
        case let .badStatus(code, detail: nil): "The server answered with status \(code)."
        case .streamEnded: "The connection closed before the answer was complete."
        }
    }

    /// True when the server gave a clear "no" (a 4xx, for example "This doesn't look like English").
    /// Asking again would give the same answer, so the app does not retry.
    var isFinalAnswer: Bool {
        guard case let .badStatus(code, _) = self else { return false }
        return (400..<500).contains(code)
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
        if let saved = await savedExplanation(forKey: key) {
            return saved
        }

        let (data, explanation) = try await askBackend(text, translatingTo: language)
        await cache?.save(data, forKey: key, kind: .sentence)
        return explanation
    }

    /// The answer we already saved for this key. A saved blob that no longer decodes counts as missing.
    private func savedExplanation(forKey key: String) async -> Explanation? {
        guard let saved = await cache?.data(forKey: key) else { return nil }
        return (try? JSONDecoder().decode(TranslationResponse.self, from: saved))?.explanation
    }

    /// Like `explain`, but asks `POST /api/translations/stream` and calls `onMeaning` as soon as the
    /// `meaning` event arrives (TASK-65). Only the final `done` answer is saved in the local cache.
    /// A cache hit returns at once and never calls `onMeaning`.
    /// Throws `BackendError` (including `.streamEnded` when the stream stops early) or a `URLError`.
    func explainStreaming(
        _ text: String,
        translatingTo language: TargetLanguage,
        onMeaning: @escaping @MainActor @Sendable (String) -> Void
    ) async throws -> Explanation {
        let key = CacheKeys.sentenceKey(text, targetLang: language.code)
        if let saved = await savedExplanation(forKey: key) {
            return saved
        }

        var request = URLRequest(url: baseURL.appending(path: "api/translations/stream"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(TranslationRequest(text: text, targetLang: language.code))

        let (bytes, response) = try await session.bytes(for: request)
        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard statusCode == 200 else {
            throw BackendError.badStatus(statusCode, detail: await problemDetail(in: bytes))
        }

        // Read line by line. (`bytes.lines` would skip the empty lines that end each event.)
        var parser = SSEParser()
        var line = Data()
        for try await byte in bytes {
            line.append(byte)
            guard byte == UInt8(ascii: "\n") else { continue }
            let events = parser.feed(line)
            line.removeAll(keepingCapacity: true)
            for event in events {
                if let explanation = try await handle(event, key: key, onMeaning: onMeaning) {
                    return explanation
                }
            }
        }
        throw BackendError.streamEnded
    }

    /// Reacts to one event. Returns the explanation when the event is `done`.
    private func handle(
        _ event: SSEEvent,
        key: String,
        onMeaning: @MainActor @Sendable (String) -> Void
    ) async throws -> Explanation? {
        let data = Data(event.data.utf8)
        switch event.name {
        case "meaning":
            if let meaning = try? JSONDecoder().decode(MeaningEvent.self, from: data) {
                await onMeaning(meaning.meaning)
            }
            return nil
        case "done":
            let explanation = try JSONDecoder().decode(TranslationResponse.self, from: data).explanation
            // The same JSON as the old endpoint, so one cache entry works for both.
            await cache?.save(data, forKey: key, kind: .sentence)
            return explanation
        case "error":
            let error = try? JSONDecoder().decode(StreamErrorEvent.self, from: data)
            throw BackendError.badStatus(error?.status ?? 502, detail: error?.message)
        default:
            return nil // An event we do not know: ignore it.
        }
    }

    /// The `detail` of a Spring error body, read from the first few KB of the stream.
    private func problemDetail(in bytes: URLSession.AsyncBytes) async -> String? {
        var body = Data()
        do {
            for try await byte in bytes {
                body.append(byte)
                if body.count >= 4096 { break }
            }
        } catch {
            return nil
        }
        return try? JSONDecoder().decode(ProblemDetail.self, from: body).detail
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
