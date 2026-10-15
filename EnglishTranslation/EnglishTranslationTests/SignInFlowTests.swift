//
//  SignInFlowTests.swift
//  EnglishTranslationTests
//
//  TASK-70: the whole flow with a fake server. A guest asks, the server says 401, the sheet opens,
//  the code is checked, and the first request runs again.
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SignInFlowTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()
    private let streamBody = "event:meaning\ndata:{\"meaning\":\"A friendly greeting.\"}\n\n"
        + "event:done\ndata:\(translationReplyJSON.replacingOccurrences(of: "\n", with: ""))\n\n"

    private let account: AccountStore

    init() {
        account = AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
    }

    /// The real backend rules, in small: a guest gets 401 for anything new, `Bearer A1` is a signed-in user,
    /// the code is `123456`.
    private func serveLikeTheBackend() {
        server.handler = { request in
            switch request.path {
            case "/api/auth/email-code":
                return .empty(204)
            case "/api/auth/verify":
                return request.json["code"] as? String == "123456"
                    ? tokenReply(access: "A1", refresh: "R1")
                    : .problem(401, code: "invalid_code")
            case "/api/translations/stream":
                return request.authorization == "Bearer A1" ? .json(streamBody) : .problem(401, code: "sign_in_required")
            case "/api/words/serendipity":
                return request.authorization == "Bearer A1" ? .json(wordReplyJSON) : .problem(401, code: "sign_in_required")
            default:
                return .empty(404)
            }
        }
    }

    private func makeStore(account: AccountStore) -> TranslationStore {
        let client = BackendClient(baseURL: server.baseURL, session: server.session, auth: account)
        return TranslationStore(
            makeTranslator: { client },
            makeWordLookup: { WordLookup(dictionary: nil, backend: client) })
    }

    /// Does what the sheet does: asks for the code, then types it.
    private func signIn(_ account: AccountStore, store: TranslationStore, code: String = "123456") async -> SignInModel {
        let model = SignInModel(account: account, onSignedIn: { await store.resumeAfterSignIn() })
        model.email = "learner@example.com"
        await model.sendCode()
        await model.updateCode(code)
        return model
    }

    // MARK: - A sentence

    @Test func aGuestSentenceOpensTheSheetAndShowsTheMessage() async {
        serveLikeTheBackend()
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."

        await store.translate()

        #expect(store.isSignInSheetOpen)
        #expect(store.textNeedingSignIn == "Hello there, my friend.")
        guard case let .failed(message, _) = store.state else {
            Issue.record("Expected the failed state, got \(store.state)")
            return
        }
        #expect(message == "Sign in to get a new explanation.")
    }

    @Test func aRightCodeClosesTheSheetAndRunsTheSentenceAgain() async {
        serveLikeTheBackend()
        let account = account
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."
        await store.translate()

        let model = await signIn(account, store: store)

        #expect(model.didSignIn)
        #expect(account.user?.email == "learner@example.com")
        #expect(store.isSignInSheetOpen == false)
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected the loaded state, got \(store.state)")
            return
        }
        #expect(result.explanation.meaning == "A friendly greeting.")
        // The first stream call was a guest; the second one carried the new token.
        let streams = server.requests(to: "/api/translations/stream")
        #expect(streams.map(\.authorization) == [nil, "Bearer A1"])
    }

    @Test func theRequestThatRunsAgainIsTheOriginalTextEvenIfTheInputChanged() async {
        serveLikeTheBackend()
        let account = account
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."
        await store.translate()
        store.inputText = "Something else I typed meanwhile."

        _ = await signIn(account, store: store)

        #expect(store.inputText == "Hello there, my friend.")
        #expect(server.requests(to: "/api/translations/stream").last?.json["text"] as? String == "Hello there, my friend.")
    }

    // MARK: - A word

    @Test func aGuestWordNotInTheBundleOpensTheSheetAndThenShowsTheWord() async {
        serveLikeTheBackend()
        let account = account
        let store = makeStore(account: account)
        store.inputText = "serendipity"

        await store.translate()
        #expect(store.isSignInSheetOpen)
        #expect(store.textNeedingSignIn == "serendipity")

        _ = await signIn(account, store: store)

        guard case let .loadedWord(entry) = store.state else {
            Issue.record("Expected a word card, got \(store.state)")
            return
        }
        #expect(entry.word == "serendipity")
        #expect(store.isSignInSheetOpen == false)
    }

    // MARK: - Not signing in

    @Test func closingTheSheetKeepsTheMessageAndSendsNothingMore() async {
        serveLikeTheBackend()
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."
        await store.translate()
        let requestsBefore = server.requests.count

        store.cancelSignIn()

        #expect(store.isSignInSheetOpen == false)
        if case .failed = store.state {} else { Issue.record("The message should stay") }
        #expect(server.requests.count == requestsBefore)
    }

    @Test func aWrongCodeKeepsTheSheetOpenAndTheRequestWaiting() async {
        serveLikeTheBackend()
        let account = account
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."
        await store.translate()

        let model = await signIn(account, store: store, code: "999999")

        #expect(model.didSignIn == false)
        #expect(model.errorMessage != nil)
        #expect(store.isSignInSheetOpen)
        #expect(account.isSignedIn == false)
        #expect(server.requests(to: "/api/translations/stream").count == 1)
    }

    @Test func aNewSearchClearsTheWaitingText() async {
        serveLikeTheBackend()
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."
        await store.translate()
        store.cancelSignIn()

        store.inputText = "A second sentence here."
        await store.translate()

        // It asked again and was refused again, so the new text is the waiting one.
        #expect(store.textNeedingSignIn == "A second sentence here.")
    }

    @Test func settingsCanOpenTheSheetWithNoRequestWaiting() async {
        serveLikeTheBackend()
        let account = account
        let store = makeStore(account: account)

        _ = await signIn(account, store: store)

        #expect(account.isSignedIn)
        #expect(store.state.resultKey == "idle")
        #expect(server.requests(to: "/api/translations/stream").isEmpty)
    }

    // MARK: - Other errors in the result area

    @Test func theDailyLimitMessageFromTheServerShowsInTheResultArea() async {
        let message = "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow."
        server.handler = { _ in .problem(503, code: "daily_limit_reached", detail: message) }
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."

        await store.translate()

        guard case let .failed(shown, _) = store.state else {
            Issue.record("Expected the failed state, got \(store.state)")
            return
        }
        #expect(shown == message)
        #expect(store.isSignInSheetOpen == false)
        // A final answer: the stream is not followed by the old endpoint.
        #expect(server.requests.count == 1)
    }

    @Test func rateLimitedShowsHowManyMinutes() async {
        server.handler = { _ in .problem(429, code: "rate_limited", headers: ["Retry-After": "1500"]) }
        let store = makeStore(account: account)
        store.inputText = "Hello there, my friend."

        await store.translate()

        guard case let .failed(shown, _) = store.state else {
            Issue.record("Expected the failed state, got \(store.state)")
            return
        }
        #expect(shown == "Too many requests. Try again in 25 minutes.")
        #expect(store.isSignInSheetOpen == false)
        #expect(server.requests.count == 1)
    }
}
