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

/// The `meaning` event of `POST /api/translations/stream`.
private struct MeaningEvent: Decodable {
    let meaning: String
}

/// The `error` event of `POST /api/translations/stream`. `status` is the HTTP status the old endpoint would use.
/// `code` is only there for the coded errors of doc-17 (`sign_in_required`, `rate_limited`, `daily_limit_reached`).
private struct StreamErrorEvent: Decodable {
    let status: Int?
    let message: String?
    let code: String?
}

/// What can go wrong when talking to our own backend.
enum BackendError: LocalizedError, Equatable {
    case badStatus(Int, detail: String?)
    /// The stream closed before the `done` event.
    case streamEnded
    /// 401 `sign_in_required`: a guest asked for something new. The app shows the sign-in sheet.
    case signInRequired
    /// 503 `daily_limit_reached`: today's Claude budget is used up. `message` is the server's text.
    case dailyLimitReached(message: String?)
    /// 429 `rate_limited`, with the seconds from the `Retry-After` header (the stream sends no header).
    case rateLimited(retryAfter: Int?)

    var errorDescription: String? {
        switch self {
        case let .badStatus(code, detail: detail?): "\(detail) (status \(code))"
        case let .badStatus(code, detail: nil): "The server answered with status \(code)."
        case .streamEnded: "The connection closed before the answer was complete."
        case .signInRequired: "Sign in to get a new explanation."
        case let .dailyLimitReached(message): message ?? Self.defaultDailyLimitMessage
        case let .rateLimited(retryAfter): RateLimitMessage.text(retryAfter: retryAfter)
        }
    }

    static let defaultDailyLimitMessage =
        "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow."

    /// True when the server gave a clear "no" (a 4xx, for example "This doesn't look like English", or the daily limit).
    /// Asking again would give the same answer, so the app does not retry.
    var isFinalAnswer: Bool {
        switch self {
        case let .badStatus(code, _): (400..<500).contains(code)
        case .signInRequired, .dailyLimitReached, .rateLimited: true
        case .streamEnded: false
        }
    }

    /// Builds the error for a failed reply. The `code` of the body wins; the status is the fallback.
    /// `retryAfter` is the `Retry-After` header in seconds.
    static func make(status: Int, problem: ProblemBody?, retryAfter: Int?) -> BackendError {
        switch (problem?.code, status) {
        case ("sign_in_required", _), (_, 401): .signInRequired
        case ("rate_limited", _), (_, 429): .rateLimited(retryAfter: retryAfter)
        case ("daily_limit_reached", _): .dailyLimitReached(message: problem?.detail)
        default: .badStatus(status, detail: problem?.detail)
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
    /// Gives the `Authorization: Bearer` token. Nil, or a guest with no token: no header is sent and nothing changes
    /// until the server answers 401 (doc-17).
    var auth: (any AccessTokenProviding)?

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

        // A 401 with a token means the token ended: refresh once and ask again (inside `withAuthRetry`).
        return try await withAuthRetry { token in
            try await readStream(request, token: token, key: key, onMeaning: onMeaning)
        }
    }

    /// Sends the stream request and reads the events until `done`.
    private func readStream(
        _ request: URLRequest,
        token: String?,
        key: String,
        onMeaning: @escaping @MainActor @Sendable (String) -> Void
    ) async throws -> Explanation {
        let (bytes, response) = try await session.bytes(for: authorized(request, token: token))
        let http = response as? HTTPURLResponse
        let statusCode = http?.statusCode ?? 0
        guard statusCode == 200 else {
            throw BackendError.make(
                status: statusCode,
                problem: await problemBody(in: bytes),
                retryAfter: Self.retryAfter(in: http))
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
            throw BackendError.make(
                status: error?.status ?? 502,
                problem: ProblemBody(detail: error?.message, code: error?.code),
                retryAfter: nil)
        default:
            return nil // An event we do not know: ignore it.
        }
    }

    /// The Spring error body (`detail` and `code`), read from the first few KB of the stream.
    private func problemBody(in bytes: URLSession.AsyncBytes) async -> ProblemBody? {
        var body = Data()
        do {
            for try await byte in bytes {
                body.append(byte)
                if body.count >= 4096 { break }
            }
        } catch {
            return nil
        }
        return try? JSONDecoder().decode(ProblemBody.self, from: body)
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

    /// Sends the request with the token (when there is one). Returns the body of a 200 reply;
    /// any other status throws a `BackendError`. A 401 with a token refreshes it once and tries again.
    private func sendChecked(_ request: URLRequest) async throws -> Data {
        try await withAuthRetry { token in
            let (data, response) = try await session.data(for: authorized(request, token: token))

            let http = response as? HTTPURLResponse
            let statusCode = http?.statusCode ?? 0
            guard statusCode == 200 else {
                let problem = try? JSONDecoder().decode(ProblemBody.self, from: data)
                throw BackendError.make(status: statusCode, problem: problem, retryAfter: Self.retryAfter(in: http))
            }
            return data
        }
    }

    // MARK: - Sign-in token

    /// Runs `operation` with the current access token (nil for a guest). If the server answers 401 and we sent a token,
    /// the token was too old: get a new one and run `operation` once more. If the refresh ends the session,
    /// the 401 goes on to the caller, which shows the sign-in sheet. A guest's 401 goes straight to the caller.
    private func withAuthRetry<T>(_ operation: (String?) async throws -> T) async throws -> T {
        let token = await auth?.accessToken()
        do {
            return try await operation(token)
        } catch BackendError.signInRequired {
            guard let auth, let token, let fresh = try await auth.refreshedAccessToken(replacing: token) else {
                throw BackendError.signInRequired
            }
            return try await operation(fresh)
        }
    }

    private func authorized(_ request: URLRequest, token: String?) -> URLRequest {
        guard let token else { return request }
        var request = request
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return request
    }

    /// The `Retry-After` header: a whole number of seconds.
    private static func retryAfter(in response: HTTPURLResponse?) -> Int? {
        response?.value(forHTTPHeaderField: "Retry-After").flatMap { Int($0) }
    }
}
