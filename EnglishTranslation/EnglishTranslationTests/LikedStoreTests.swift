//
//  LikedStoreTests.swift
//  EnglishTranslationTests
//
//  TASK-93.2: the liked list (guest, like, unlike, offline copy), the API calls, and History in TranslationStore.
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A liked API in memory. Remembers the calls; `failure` makes every call throw.
private final class FakeLikedAPI: LikedAPI, @unchecked Sendable {
    private let lock = NSLock()
    private var _server: [SavedItem]
    private var _calls: [String] = []
    private var _failure: Error?

    init(server: [SavedItem] = []) { _server = server }

    var calls: [String] { lock.withLock { _calls } }
    var failure: Error? {
        get { lock.withLock { _failure } }
        set { lock.withLock { _failure = newValue } }
    }

    func likedItems() async throws -> [SavedItem] {
        try record("list")
        return lock.withLock { _server }
    }

    func like(_ item: SavedItem) async throws {
        try record("like \(item.text)")
        lock.withLock { _server = [item] + _server.filter { $0.id != item.id } }
    }

    func unlike(kind: SavedItem.Kind, text: String) async throws {
        try record("unlike \(text)")
        lock.withLock { _server.removeAll { $0.id == SavedItem.key(kind: kind, text: text) } }
    }

    private func record(_ call: String) throws {
        try lock.withLock {
            _calls.append(call)
            if let _failure { throw _failure }
        }
    }
}

private final class MemoryCopy: LikedCopyStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [SavedItem]
    init(_ items: [SavedItem] = []) { stored = items }
    func load() -> [SavedItem] { lock.withLock { stored } }
    func save(_ items: [SavedItem]) { lock.withLock { stored = items } }
}

private let apple = SavedItem(kind: .word, text: "apple", preview: "a fruit", chip: "noun · A1")
private let sentence = SavedItem(kind: .sentence, text: "The fog was thick.", preview: "It was hard to see.")

@MainActor
struct LikedStoreTests {
    private func makeStore(
        api: FakeLikedAPI = FakeLikedAPI(), copy: MemoryCopy = MemoryCopy(), signedIn: Bool = true
    ) -> LikedStore {
        LikedStore(makeAPI: { api }, copy: copy, isSignedIn: { signedIn })
    }

    @Test func likingShowsAtOnceAndIsSentToTheServer() async {
        let api = FakeLikedAPI()
        let copy = MemoryCopy()
        let store = makeStore(api: api, copy: copy)

        await store.toggle(apple)

        #expect(store.isLiked(apple))
        #expect(api.calls == ["like apple"])
        #expect(copy.load() == [apple])
    }

    @Test func tappingAgainUnlikes() async {
        let api = FakeLikedAPI()
        let store = makeStore(api: api)

        await store.toggle(apple)
        await store.toggle(apple)

        #expect(!store.isLiked(apple))
        #expect(api.calls == ["like apple", "unlike apple"])
    }

    @Test func aGuestGetsTheSignInSheetAndNothingIsSent() async {
        let api = FakeLikedAPI()
        let store = makeStore(api: api, signedIn: false)

        await store.toggle(apple)

        #expect(store.signInRequested)
        #expect(store.pending == apple)
        #expect(!store.isLiked(apple))
        #expect(api.calls.isEmpty)
    }

    @Test func afterSignInThePendingLikeIsSentAndTheListLoads() async {
        let api = FakeLikedAPI(server: [sentence])
        var signedIn = false
        let store = LikedStore(makeAPI: { api }, copy: MemoryCopy(), isSignedIn: { signedIn })
        await store.toggle(apple)

        signedIn = true
        await store.signedIn()

        #expect(api.calls == ["like apple", "list"])
        #expect(store.isLiked(apple))
        #expect(store.isLiked(sentence))
        #expect(store.pending == nil)
    }

    @Test func closingTheSheetDropsThePendingLike() async {
        let store = makeStore(signedIn: false)
        await store.toggle(apple)
        store.cancelPending()
        #expect(store.pending == nil)
    }

    @Test func aFailedLikeIsUndone() async {
        let api = FakeLikedAPI()
        api.failure = URLError(.notConnectedToInternet)
        let store = makeStore(api: api)

        await store.toggle(apple)

        #expect(!store.isLiked(apple))
        #expect(store.errorMessage != nil)
    }

    @Test func anEndedSessionAsksForSignInAgain() async {
        let api = FakeLikedAPI()
        api.failure = BackendError.signInRequired
        let store = makeStore(api: api)

        await store.toggle(apple)

        #expect(store.signInRequested)
        #expect(store.pending == apple)
        #expect(!store.isLiked(apple))
    }

    @Test func theCopyOnThePhoneShowsAtOnceAndOffline() async {
        let api = FakeLikedAPI()
        api.failure = URLError(.notConnectedToInternet)
        let store = makeStore(api: api, copy: MemoryCopy([apple]))

        #expect(store.isLiked(apple))
        await store.refresh()
        #expect(store.items == [apple])
    }

    @Test func refreshTakesTheServerListAndKeepsTheLabels() async {
        let fromOtherDevice = SavedItem(kind: .word, text: "apple", preview: "a fruit (server)")
        let api = FakeLikedAPI(server: [fromOtherDevice, sentence])
        let store = makeStore(api: api, copy: MemoryCopy([apple]))

        await store.refresh()

        #expect(store.items.map(\.text) == ["apple", "The fog was thick."])
        #expect(store.items.first?.chip == "noun · A1")
        #expect(store.items.first?.preview == "a fruit (server)")
    }

    @Test func signingOutClearsTheCopy() {
        let copy = MemoryCopy([apple])
        let store = makeStore(copy: copy)
        store.clear()
        #expect(store.items.isEmpty)
        #expect(copy.load().isEmpty)
    }

    @Test func swipeToUnlike() async {
        let api = FakeLikedAPI()
        let store = makeStore(api: api, copy: MemoryCopy([apple, sentence]))
        await store.unlike(id: apple.id)
        #expect(store.items == [sentence])
        #expect(api.calls == ["unlike apple"])
    }
}

/// The three calls of `/api/liked` through `BackendClient`, with a fake server.
struct LikedAPITests {
    private let server = ScriptedServer()

    private func client(token: String? = "A1") -> BackendClient {
        let secrets = FakeSecretStore()
        if token != nil { try? secrets.setSecret(savedSessionJSON(access: "A1", refresh: "R1"), forKey: "session") }
        return BackendClient(
            baseURL: server.baseURL, session: server.session,
            auth: AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session)))
    }

    @Test func listReadsItemsWithAndWithoutFractionalSeconds() async throws {
        server.handler = { _ in .json("""
            {"items":[
              {"kind":"word","text":"apple","preview":"a fruit","createdAt":"2026-10-19T10:00:00.123Z"},
              {"kind":"sentence","text":"Hi there.","preview":"A greeting.","createdAt":"2026-10-18T09:30:00Z"}]}
            """) }

        let items = try await client().likedItems()

        #expect(items.map(\.text) == ["apple", "Hi there."])
        #expect(items[1].kind == .sentence)
        #expect(server.requests.first?.authorization == "Bearer A1")
        #expect(server.requests.first?.path == "/api/liked")
    }

    @Test func likeSendsAPutWithTheKindTextAndPreview() async throws {
        server.handler = { _ in .json(#"{"kind":"word","text":"apple","preview":"a fruit","createdAt":"2026-10-19T10:00:00Z"}"#) }

        try await client().like(SavedItem(kind: .word, text: "apple", preview: "a fruit"))

        let request = try #require(server.requests.first)
        #expect(request.method == "PUT")
        #expect(request.json["kind"] as? String == "word")
        #expect(request.json["text"] as? String == "apple")
        #expect(request.json["preview"] as? String == "a fruit")
    }

    @Test func unlikeSendsADeleteAndAcceptsNoContent() async throws {
        server.handler = { _ in .empty(204) }

        try await client().unlike(kind: .sentence, text: "Hi there & bye.")

        let request = try #require(server.requests.first)
        #expect(request.method == "DELETE")
        #expect(request.query.contains("kind=sentence"))
        #expect(request.query.contains("%26"))
    }

    @Test func aGuestGetsSignInRequired() async {
        server.handler = { _ in .problem(401, code: "sign_in_required") }

        await #expect(throws: BackendError.signInRequired) {
            _ = try await client(token: nil).likedItems()
        }
    }
}

/// History is written where the final result is set (TASK-93.2).
@MainActor
struct TranslationStoreHistoryTests {
    private struct FakeTranslator: Translating {
        var result: Result<Explanation, Error>
        func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation { try result.get() }
        func explainStreaming(
            _ text: String, translatingTo language: TargetLanguage,
            onMeaning: @escaping @MainActor @Sendable (String) -> Void
        ) async throws -> Explanation { try result.get() }
    }

    private struct FakeWords: WordLooking {
        var entry: WordEntry?
        var phrase: WordEntry?
        func lookUp(_ word: String) async throws -> WordEntry {
            guard let entry else { throw URLError(.notConnectedToInternet) }
            return entry
        }
        func phraseEntry(for phrase: String) async -> WordEntry? { self.phrase }
    }

    private let sense = Sense(pos: "noun", cefr: "A1", simpleDefinition: "a fruit", vietnamese: "quả táo", examples: [])

    private func makeStore(history: MemoryHistory, words: FakeWords = FakeWords(),
                           sentence: Result<Explanation, Error> = .failure(URLError(.badServerResponse))) -> TranslationStore {
        TranslationStore(
            makeTranslator: { FakeTranslator(result: sentence) },
            makeWordLookup: { words },
            history: history)
    }

    @Test func aWordResultIsRecorded() async {
        let history = MemoryHistory()
        let store = makeStore(history: history, words: FakeWords(entry: WordEntry(word: "apple", ipa: nil, senses: [sense])))
        store.inputText = "apple"
        await store.translate()
        #expect(await history.items().map(\.text) == ["apple"])
    }

    @Test func aPhraseHitIsRecorded() async {
        let history = MemoryHistory()
        let phrase = WordEntry(word: "give up", ipa: nil, senses: [sense])
        let store = makeStore(history: history, words: FakeWords(phrase: phrase))
        store.inputText = "give up"
        await store.translate()
        #expect(await history.items().map(\.text) == ["give up"])
    }

    @Test func aSentenceResultIsRecorded() async {
        let history = MemoryHistory()
        let explanation = Explanation(meaning: "A greeting.", words: [], translation: "Xin chào")
        let store = makeStore(history: history, sentence: .success(explanation))
        store.inputText = "Hello there my friend."
        await store.translate()
        let items = await history.items()
        #expect(items.map(\.text) == ["Hello there my friend."])
        #expect(items.first?.preview == "A greeting.")
    }

    @Test func aFailureIsNotRecorded() async {
        let history = MemoryHistory()
        let store = makeStore(history: history)
        store.inputText = "apple"
        await store.translate()
        store.inputText = "Hello there my friend."
        await store.translate()
        #expect(await history.items().isEmpty)
    }
}
