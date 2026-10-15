//
//  BackendClientAuthTests.swift
//  EnglishTranslationTests
//
//  TASK-70: the Bearer header, refresh-then-retry on 401, and the coded errors of doc-17.
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct BackendClientAuthTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()

    /// An account that is signed in with access token `A1` and refresh token `R1`.
    private func signedInAccount() -> AccountStore {
        try? secrets.setSecret(savedSessionJSON(access: "A1", refresh: "R1"), forKey: "session")
        return AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
    }

    private func makeClient(auth: (any AccessTokenProviding)?) -> BackendClient {
        BackendClient(baseURL: server.baseURL, session: server.session, auth: auth)
    }

    private static let streamBody = "event:meaning\ndata:{\"meaning\":\"A friendly greeting.\"}\n\n"
        + "event:done\ndata:\(translationReplyJSON.replacingOccurrences(of: "\n", with: ""))\n\n"

    /// A fake server that follows the real rules: a request with `Bearer A2` works, anything else is 401.
    private func serveOnlyTokenA2(okBody: String, refreshTo: ScriptedReply? = nil) {
        server.handler = { request in
            if request.path == "/api/auth/refresh" { return refreshTo ?? tokenReply(access: "A2", refresh: "R2") }
            guard request.authorization == "Bearer A2" else { return .problem(401, code: "sign_in_required") }
            return .json(okBody)
        }
    }

    // MARK: - The header

    @Test func aGuestSendsNoAuthorizationHeader() async throws {
        server.handler = { _ in .json(translationReplyJSON) }

        _ = try await makeClient(auth: AccountStore(secrets: FakeSecretStore())).explain("Hello there.", translatingTo: .vietnamese)

        #expect(server.requests.first?.authorization == nil)
    }

    @Test func noTokenProviderAtAllStillWorks() async throws {
        server.handler = { _ in .json(translationReplyJSON) }

        _ = try await makeClient(auth: nil).explain("Hello there.", translatingTo: .vietnamese)

        #expect(server.requests.count == 1)
    }

    @Test func translationsStreamAndWordsAllSendTheBearerToken() async throws {
        server.handler = { request in
            switch request.path {
            case "/api/translations/stream": .json(Self.streamBody)
            case "/api/translations": .json(translationReplyJSON)
            default: .json(wordReplyJSON)
            }
        }
        let client = makeClient(auth: signedInAccount())

        _ = try await client.explain("Hello there.", translatingTo: .vietnamese)
        _ = try await client.explainStreaming("Hello again.", translatingTo: .vietnamese) { _ in }
        _ = try await client.entry(forWord: "serendipity")

        #expect(server.requests.map(\.path) == ["/api/translations", "/api/translations/stream", "/api/words/serendipity"])
        #expect(server.requests.allSatisfy { $0.authorization == "Bearer A1" })
    }

    // MARK: - 401: refresh once, then try again

    @Test func anEndedTokenIsRefreshedAndTheTranslationRunsAgain() async throws {
        serveOnlyTokenA2(okBody: translationReplyJSON)
        let account = signedInAccount()

        let explanation = try await makeClient(auth: account).explain("Hello there.", translatingTo: .vietnamese)

        #expect(explanation.meaning == "A friendly greeting.")
        #expect(server.requests.map(\.path) == ["/api/translations", "/api/auth/refresh", "/api/translations"])
        #expect(server.requests.map(\.authorization) == ["Bearer A1", nil, "Bearer A2"])
        #expect(await account.accessToken() == "A2")
    }

    @Test func anEndedTokenIsRefreshedForTheStreamToo() async throws {
        serveOnlyTokenA2(okBody: Self.streamBody)

        let explanation = try await makeClient(auth: signedInAccount())
            .explainStreaming("Hello there.", translatingTo: .vietnamese) { _ in }

        #expect(explanation.meaning == "A friendly greeting.")
        #expect(server.requests.map(\.path) == ["/api/translations/stream", "/api/auth/refresh", "/api/translations/stream"])
        #expect(server.requests.last?.authorization == "Bearer A2")
    }

    @Test func anEndedTokenIsRefreshedForWordsToo() async throws {
        serveOnlyTokenA2(okBody: wordReplyJSON)

        let entry = try await makeClient(auth: signedInAccount()).entry(forWord: "serendipity")

        #expect(entry.word == "serendipity")
        #expect(server.requests.map(\.path) == ["/api/words/serendipity", "/api/auth/refresh", "/api/words/serendipity"])
    }

    @Test func aSignInRequiredErrorEventInTheStreamAlsoRefreshesAndRetries() async throws {
        let errorEvent = "event:error\ndata:{\"status\":401,\"message\":\"Sign in.\",\"code\":\"sign_in_required\"}\n\n"
        server.handler = { request in
            if request.path == "/api/auth/refresh" { return tokenReply(access: "A2", refresh: "R2") }
            return .json(request.authorization == "Bearer A2" ? Self.streamBody : errorEvent)
        }

        let explanation = try await makeClient(auth: signedInAccount())
            .explainStreaming("Hello there.", translatingTo: .vietnamese) { _ in }

        #expect(explanation.meaning == "A friendly greeting.")
        #expect(server.requests.count == 3)
    }

    @Test func aRefusedRefreshSignsOutAndTheCallerGetsSignInRequired() async {
        server.handler = { request in
            request.path == "/api/auth/refresh"
                ? .problem(401, code: "invalid_refresh_token")
                : .problem(401, code: "sign_in_required")
        }
        let account = signedInAccount()

        await #expect(throws: BackendError.signInRequired) {
            _ = try await makeClient(auth: account).explain("Hello there.", translatingTo: .vietnamese)
        }

        #expect(account.isSignedIn == false)
        #expect(secrets.secret(forKey: "session") == nil)
    }

    @Test func itRetriesOnlyOnce() async {
        // The server says 401 even for the new token. The client must not loop.
        server.handler = { request in
            request.path == "/api/auth/refresh"
                ? tokenReply(access: "A2", refresh: "R2")
                : .problem(401, code: "sign_in_required")
        }

        await #expect(throws: BackendError.signInRequired) {
            _ = try await makeClient(auth: signedInAccount()).explain("Hello there.", translatingTo: .vietnamese)
        }

        #expect(server.requests.map(\.path) == ["/api/translations", "/api/auth/refresh", "/api/translations"])
    }

    @Test func aGuestGets401WithNoRefreshAttempt() async {
        server.handler = { _ in .problem(401, code: "sign_in_required") }

        await #expect(throws: BackendError.signInRequired) {
            _ = try await makeClient(auth: AccountStore(secrets: FakeSecretStore())).entry(forWord: "serendipity")
        }

        #expect(server.requests.count == 1)
    }

    // MARK: - The coded errors

    @Test func rateLimitedReadsRetryAfterAndSaysMinutes() async {
        server.handler = { _ in .problem(429, code: "rate_limited", headers: ["Retry-After": "600"]) }

        do {
            _ = try await makeClient(auth: nil).explain("Hello there.", translatingTo: .vietnamese)
            Issue.record("Expected an error")
        } catch {
            #expect(error as? BackendError == .rateLimited(retryAfter: 600))
            #expect(error.localizedDescription == "Too many requests. Try again in 10 minutes.")
        }
    }

    @Test func aPlain429WithoutACodeIsStillRateLimited() async {
        server.handler = { _ in .empty(429) }

        await #expect(throws: BackendError.rateLimited(retryAfter: nil)) {
            _ = try await makeClient(auth: nil).entry(forWord: "serendipity")
        }
    }

    @Test func dailyLimitShowsTheServerMessage() async {
        let message = "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow."
        server.handler = { _ in .problem(503, code: "daily_limit_reached", detail: message) }

        do {
            _ = try await makeClient(auth: nil).explain("Hello there.", translatingTo: .vietnamese)
            Issue.record("Expected an error")
        } catch {
            #expect(error as? BackendError == .dailyLimitReached(message: message))
            #expect(error.localizedDescription == message)
        }
    }

    @Test func anotherServiceUnavailableStaysABadStatus() async {
        server.handler = { _ in .problem(503, code: nil, detail: "Down") }

        await #expect(throws: BackendError.badStatus(503, detail: "Down")) {
            _ = try await makeClient(auth: nil).explain("Hello there.", translatingTo: .vietnamese)
        }
    }

    @Test func theStreamErrorEventCarriesTheSameCodes() async {
        let events = [
            "{\"status\":401,\"message\":\"Sign in to get a new explanation.\",\"code\":\"sign_in_required\"}": BackendError.signInRequired,
            "{\"status\":429,\"message\":\"Too many requests.\",\"code\":\"rate_limited\"}": BackendError.rateLimited(retryAfter: nil),
            "{\"status\":503,\"message\":\"Limit reached.\",\"code\":\"daily_limit_reached\"}": BackendError.dailyLimitReached(message: "Limit reached."),
        ]
        for (data, expected) in events {
            server.handler = { _ in .json("event:error\ndata:\(data)\n\n") }
            await #expect(throws: expected) {
                _ = try await makeClient(auth: nil).explainStreaming("Hello there.", translatingTo: .vietnamese) { _ in }
            }
        }
    }

    @Test func theNewErrorsAreFinalAnswersSoTheStreamIsNotRetried() {
        #expect(BackendError.signInRequired.isFinalAnswer)
        #expect(BackendError.rateLimited(retryAfter: 60).isFinalAnswer)
        #expect(BackendError.dailyLimitReached(message: nil).isFinalAnswer)
        #expect(BackendError.signInRequired.localizedDescription == "Sign in to get a new explanation.")
    }
}
