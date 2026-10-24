//
//  LanguageLinesTests.swift
//  EnglishTranslationTests
//
//  TASK-106.3: the word language row gets its line from POST /api/translate-lines.
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A backend that answers from a table and counts the requests.
private final class FakeLineBackend: LineTranslating, @unchecked Sendable {
    var table: [String: String] = [:]
    var error: Error?
    var delay: Duration?
    private(set) var requests: [[String]] = []

    func lines(for texts: [String], in language: TargetLanguage) async throws -> [String?] {
        requests.append(texts)
        if let delay { try await Task.sleep(for: delay) }
        if let error { throw error }
        return texts.map { table[$0] }
    }
}

@MainActor
struct LanguageLinesTests {
    private let backend = FakeLineBackend()

    private func makeLines() -> LanguageLines {
        LanguageLines(backend: { [backend] in backend }, cache: TranslationCache())
    }

    private func makeExplainer(_ language: TargetLanguage) -> QuickExplainer {
        var explainer = QuickExplainer(
            makeWordLookup: { throw BackendError.streamEnded },
            makeTranslator: { throw BackendError.streamEnded })
        explainer.language = language
        explainer.lines = makeLines()
        return explainer
    }

    // MARK: - Loader

    @Test func oneRequestAsksForEverySenseOfTheCard() async {
        backend.table = ["a": "A-ko", "b": "B-ko", "c": "C-ko"]
        let lines = makeLines()

        await lines.load(["a", "b", "c"], in: .korean)

        #expect(backend.requests == [["a", "b", "c"]])
        #expect(lines.line(for: "b", in: .korean) == "B-ko")
    }

    @Test func openingAnotherSenseSendsNoNewRequest() async {
        backend.table = ["a": "A-ko", "b": "B-ko"]
        let lines = makeLines()
        await lines.load(["a", "b"], in: .korean)

        await lines.load(["a", "b"], in: .korean)

        #expect(backend.requests.count == 1)
    }

    @Test func rowsThatOpenTogetherShareOneRequest() async {
        backend.table = ["a": "A-ko", "b": "B-ko"]
        backend.delay = .milliseconds(50)
        let lines = makeLines()

        async let first: Void = lines.load(["a", "b"], in: .korean)
        async let second: Void = lines.load(["a", "b"], in: .korean)
        _ = await (first, second)

        #expect(backend.requests.count == 1)
        #expect(lines.line(for: "a", in: .korean) == "A-ko")
    }

    @Test func linesAreKeptPerLanguage() async {
        backend.table = ["a": "A"]
        let lines = makeLines()
        await lines.load(["a"], in: .korean)

        #expect(lines.line(for: "a", in: .korean) == "A")
        #expect(lines.line(for: "a", in: .japanese) == nil)
    }

    @Test func aMissingLineStaysMissingAndIsAskedAgainLater() async {
        let lines = makeLines()
        await lines.load(["a"], in: .korean)
        #expect(lines.line(for: "a", in: .korean) == nil)

        backend.table = ["a": "A-ko"]
        await lines.load(["a"], in: .korean)
        #expect(lines.line(for: "a", in: .korean) == "A-ko")
    }

    @Test func aFailedCallLeavesNoLineAndDoesNotThrow() async {
        backend.error = BackendError.badStatus(503, detail: nil)
        let lines = makeLines()

        await lines.load(["a"], in: .korean)

        #expect(lines.line(for: "a", in: .korean) == nil)
    }

    // MARK: - Fallbacks and rows

    @Test func vietnameseFallsBackToTheDictionaryText() async {
        backend.error = URLError(.notConnectedToInternet)
        let sense = WordEntry.depend.senses[0]
        let lines = makeLines()

        await lines.load([sense.simpleDefinition], in: .vietnamese)

        #expect(lines.line(for: sense.simpleDefinition, in: .vietnamese) == nil)
        #expect(sense.dictionaryText(for: .vietnamese) == sense.vietnamese)
    }

    @Test func otherLanguagesHaveNoDictionaryTextSoTheyUseAppleTranslation() {
        #expect(WordEntry.depend.senses[0].dictionaryText(for: .korean) == nil)
        #expect(TargetLanguage.korean.needsOnDeviceTranslation)
    }

    @Test func everyLanguageExceptEnglishGetsARow() {
        let sense = WordEntry.sparse.senses[0] // no dictionary Vietnamese
        for language in TargetLanguage.allCases {
            #expect(sense.hasTargetLanguageRow(for: language) == (language != .english))
        }
    }

    // MARK: - Quick explain

    @Test func quickExplainUsesTheBackendLineForAWord() async {
        let first = WordEntry.run.senses[0].simpleDefinition
        backend.table = [first: "달리다"]

        let line = await makeExplainer(.korean).languageLine(for: .word(.run))

        #expect(line == "달리다")
        #expect(backend.requests == [[first]])
    }

    @Test func quickExplainGivesNoLineWhenTheBackendFails() async {
        backend.error = BackendError.signInRequired

        #expect(await makeExplainer(.korean).languageLine(for: .word(.run)) == nil)
    }

    @Test func quickExplainAsksNothingForEnglishOrASentence() async {
        #expect(await makeExplainer(.english).languageLine(for: .word(.run)) == nil)

        let sentence = QuickExplainResult.sentence("Hi there.", Explanation(meaning: "m", words: [], translation: "t"))
        #expect(await makeExplainer(.korean).languageLine(for: sentence) == nil)
        #expect(backend.requests.isEmpty)
    }

    @Test func quickExplainStopsWaitingAfterTheTimeLimit() async {
        backend.table = [WordEntry.run.senses[0].simpleDefinition: "달리다"]
        backend.delay = .seconds(5)
        var explainer = makeExplainer(.korean)
        explainer.lineTimeLimit = .milliseconds(50)

        #expect(await explainer.languageLine(for: .word(.run)) == nil)
    }
}

/// The real client: what goes over the wire for a signed-in user and for a guest.
@MainActor
struct BackendLinesRequestTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()

    private func client(signedIn: Bool) -> BackendClient {
        let account: AccountStore
        if signedIn {
            try? secrets.setSecret(savedSessionJSON(access: "A1", refresh: "R1"), forKey: "session")
            account = AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
        } else {
            account = AccountStore(secrets: secrets)
        }
        return BackendClient(baseURL: server.baseURL, session: server.session, auth: account)
    }

    private let reply = """
        {"targetLang":"ko","lines":[{"text":"a","translation":"A-ko","cached":true},{"text":"b","translation":null,"cached":false}]}
        """

    @Test func signedInSendsANormalCallWithTheToken() async throws {
        let reply = reply
        server.handler = { _ in .json(reply) }

        let lines = try await client(signedIn: true).lines(for: ["a", "b"], in: .korean)

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/translate-lines")
        #expect(request.query == "")
        #expect(request.authorization == "Bearer A1")
        #expect(request.json["targetLang"] as? String == "ko")
        #expect(request.json["texts"] as? [String] == ["a", "b"])
        #expect(lines == ["A-ko", nil])
    }

    @Test func guestAsksCachedOnlyAndAMissComesBackNil() async throws {
        let reply = reply
        server.handler = { _ in .json(reply) }

        let lines = try await client(signedIn: false).lines(for: ["a", "b"], in: .korean)

        let request = try #require(server.requests.first)
        #expect(request.query == "cachedOnly=true")
        #expect(request.authorization == nil)
        #expect(lines == ["A-ko", nil])
    }

    @Test func aFailedCallThrowsSoTheRowCanFallBack() async throws {
        server.handler = { _ in .problem(503, code: "daily_limit_reached") }
        let client = client(signedIn: true)

        await #expect(throws: BackendError.self) {
            _ = try await client.lines(for: ["a"], in: .korean)
        }
    }

    @Test func moreThanTwentyTextsAreSentInGroups() async throws {
        server.handler = { request in
            let texts = request.json["texts"] as? [String] ?? []
            let items = texts.map { "{\"text\":\"\($0)\",\"translation\":\"T\($0)\",\"cached\":true}" }
            return .json("{\"targetLang\":\"ko\",\"lines\":[\(items.joined(separator: ","))]}")
        }
        let texts = (1...25).map(String.init)

        let lines = try await client(signedIn: true).lines(for: texts, in: .korean)

        #expect(server.requests.count == 2)
        #expect(lines.count == 25)
        #expect(lines.last == "T25")
    }
}
