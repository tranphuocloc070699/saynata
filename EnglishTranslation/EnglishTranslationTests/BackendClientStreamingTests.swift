//
//  BackendClientStreamingTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// Answers a request with a status and a body that arrives in several chunks, like a real event stream.
private final class ChunkedStubURLProtocol: URLProtocol {
    nonisolated(unsafe) static var status = 200
    nonisolated(unsafe) static var chunks: [Data] = []
    nonisolated(unsafe) static var lastRequest: URLRequest?
    nonisolated(unsafe) static var requestCount = 0

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lastRequest = request
        Self.requestCount += 1
        let response = HTTPURLResponse(url: request.url!, statusCode: Self.status, httpVersion: nil,
                                       headerFields: ["Content-Type": "text/event-stream"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        for chunk in Self.chunks {
            client?.urlProtocol(self, didLoad: chunk)
        }
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    /// Sets the body, cut into pieces of `size` bytes.
    static func serve(_ body: String, status: Int = 200, chunkSize: Int = 7) {
        self.status = status
        let bytes = Array(body.utf8)
        chunks = stride(from: 0, to: bytes.count, by: chunkSize).map {
            Data(bytes[$0..<min($0 + chunkSize, bytes.count)])
        }
        requestCount = 0
    }
}

/// Collects the meanings the client reports. Main-actor, so the `onMeaning` closure can use it.
@MainActor
private final class MeaningLog {
    var meanings: [String] = []
}

@MainActor
@Suite(.serialized)
struct BackendClientStreamingTests {
    private let client: BackendClient = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ChunkedStubURLProtocol.self]
        return BackendClient(baseURL: URL(string: "http://localhost:8081")!, session: URLSession(configuration: configuration))
    }()

    private static let doneData = #"{"text":"Hi","targetLang":"vi","explanation":{"meaning":"A greeting.","words":[{"word":"hi","partOfSpeech":"interjection","meaning":"hello"}],"translation":"Xin chào, đây là lời chào"},"cached":false}"#

    private static let meaningThenDone = "event:meaning\ndata:{\"meaning\":\"A greeting.\"}\n\n"
        + "event:done\ndata:\(doneData)\n\n"

    @Test func postsToTheStreamEndpointAsAnEventStream() async throws {
        ChunkedStubURLProtocol.serve(Self.meaningThenDone)

        _ = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }

        let request = try #require(ChunkedStubURLProtocol.lastRequest)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.absoluteString == "http://localhost:8081/api/translations/stream")
        #expect(request.value(forHTTPHeaderField: "Accept") == "text/event-stream")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
    }

    @Test(arguments: [1, 3, 7, 50, 10_000])
    func meaningThenDoneWorksWhateverTheChunkSize(chunkSize: Int) async throws {
        ChunkedStubURLProtocol.serve(Self.meaningThenDone, chunkSize: chunkSize)
        let log = MeaningLog()

        let explanation = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { meaning in
            log.meanings.append(meaning)
        }

        #expect(log.meanings == ["A greeting."])
        #expect(explanation.meaning == "A greeting.")
        #expect(explanation.words.map(\.word) == ["hi"])
        #expect(explanation.translation == "Xin chào, đây là lời chào")
    }

    // MARK: - meaning-delta (TASK-73)

    private static let deltasThenMeaningThenDone = "event:meaning-delta\ndata:{\"text\":\"A \"}\n\n"
        + "event:meaning-delta\ndata:{\"text\":\"greet\"}\n\n"
        + "event:meaning\ndata:{\"meaning\":\"A greeting.\"}\n\n"
        + "event:done\ndata:\(doneData)\n\n"

    @Test(arguments: [1, 5, 10_000])
    func deltasArriveInOrderBeforeTheMeaning(chunkSize: Int) async throws {
        ChunkedStubURLProtocol.serve(Self.deltasThenMeaningThenDone, chunkSize: chunkSize)
        let log = MeaningLog()

        let explanation = try await client.explainStreaming(
            "Hi",
            translatingTo: .vietnamese,
            onMeaningDelta: { log.meanings.append("delta:" + $0) },
            onMeaning: { log.meanings.append("meaning:" + $0) })

        #expect(log.meanings == ["delta:A ", "delta:greet", "meaning:A greeting."])
        #expect(explanation.meaning == "A greeting.")
    }

    @Test func theOldCallIgnoresDeltasAndStillWorks() async throws {
        ChunkedStubURLProtocol.serve(Self.deltasThenMeaningThenDone)
        let log = MeaningLog()

        let explanation = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { log.meanings.append($0) }

        #expect(log.meanings == ["A greeting."])
        #expect(explanation.words.map(\.word) == ["hi"])
    }

    @Test func aBrokenDeltaEventIsIgnored() async throws {
        ChunkedStubURLProtocol.serve("event:meaning-delta\ndata:not json\n\nevent:done\ndata:\(Self.doneData)\n\n")
        let log = MeaningLog()

        let explanation = try await client.explainStreaming(
            "Hi", translatingTo: .vietnamese,
            onMeaningDelta: { log.meanings.append($0) }, onMeaning: { log.meanings.append($0) })

        #expect(log.meanings.isEmpty)
        #expect(explanation.meaning == "A greeting.")
    }

    @Test func aCacheHitSendsOnlyDoneSoThereIsNoMeaningCall() async throws {
        ChunkedStubURLProtocol.serve("event:done\ndata:\(Self.doneData)\n\n")
        let log = MeaningLog()

        let explanation = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { log.meanings.append($0) }

        #expect(log.meanings.isEmpty)
        #expect(explanation.meaning == "A greeting.")
    }

    @Test func onlyTheDoneResultIsSavedInTheLocalCache() async throws {
        var cachedClient = client
        let cache = try ResultCache(fileURL: nil)
        cachedClient.cache = cache
        // The stream ends after the meaning: nothing may be saved.
        ChunkedStubURLProtocol.serve("event:meaning\ndata:{\"meaning\":\"A greeting.\"}\n\n")
        _ = try? await cachedClient.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        let key = CacheKeys.sentenceKey("Hi", targetLang: "vi")
        #expect(await cache.data(forKey: key) == nil)

        ChunkedStubURLProtocol.serve(Self.meaningThenDone)
        _ = try await cachedClient.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        #expect(await cache.data(forKey: key) != nil)
    }

    @Test func aSavedResultIsServedWithNoNetworkAndNoMeaningCall() async throws {
        var cachedClient = client
        cachedClient.cache = try ResultCache(fileURL: nil)
        ChunkedStubURLProtocol.serve(Self.meaningThenDone)
        _ = try await cachedClient.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        let log = MeaningLog()

        let again = try await cachedClient.explainStreaming(" Hi ", translatingTo: .vietnamese) { log.meanings.append($0) }

        #expect(ChunkedStubURLProtocol.requestCount == 1)
        #expect(log.meanings.isEmpty)
        #expect(again.meaning == "A greeting.")
    }

    @Test func theOldEndpointAndTheStreamShareOneCacheEntry() async throws {
        var cachedClient = client
        cachedClient.cache = try ResultCache(fileURL: nil)
        ChunkedStubURLProtocol.serve(Self.meaningThenDone)
        _ = try await cachedClient.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        ChunkedStubURLProtocol.requestCount = 0

        let explanation = try await cachedClient.explain("Hi", translatingTo: .vietnamese)

        #expect(ChunkedStubURLProtocol.requestCount == 0)
        #expect(explanation.meaning == "A greeting.")
    }

    @Test func anErrorEventThrowsItsStatusAndMessage() async {
        ChunkedStubURLProtocol.serve(
            "event:error\ndata:{\"status\":422,\"message\":\"This doesn't look like English.\"}\n\n")

        await #expect(throws: BackendError.badStatus(422, detail: "This doesn't look like English.")) {
            _ = try await client.explainStreaming("zzxq", translatingTo: .vietnamese) { _ in }
        }
    }

    @Test func aStreamThatEndsBeforeDoneThrows() async {
        ChunkedStubURLProtocol.serve("event:meaning\ndata:{\"meaning\":\"A greeting.\"}\n\n")

        await #expect(throws: BackendError.streamEnded) {
            _ = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        }
    }

    @Test func aBadStatusBeforeTheStreamStartsReadsTheErrorDetail() async {
        ChunkedStubURLProtocol.serve(
            #"{"title":"Bad Request","status":400,"detail":"Text must be 1-500 characters."}"#, status: 400)

        await #expect(throws: BackendError.badStatus(400, detail: "Text must be 1-500 characters.")) {
            _ = try await client.explainStreaming("Hi", translatingTo: .vietnamese) { _ in }
        }
    }

    @Test func onlyA4xxIsAFinalAnswer() {
        #expect(BackendError.badStatus(422, detail: nil).isFinalAnswer)
        #expect(BackendError.badStatus(400, detail: nil).isFinalAnswer)
        #expect(!BackendError.badStatus(502, detail: nil).isFinalAnswer)
        #expect(BackendError.badStatus(404, detail: nil).isFinalAnswer)
        #expect(!BackendError.streamEnded.isFinalAnswer)
    }
}
