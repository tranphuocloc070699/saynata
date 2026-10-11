//
//  BackendClientTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// Answers every request with a fixed status and body, and remembers the last request.
/// Lets `BackendClient` run its real code with no server.
private final class StubURLProtocol: URLProtocol {
    nonisolated(unsafe) static var status = 200
    nonisolated(unsafe) static var body = Data()
    nonisolated(unsafe) static var lastRequest: URLRequest?
    nonisolated(unsafe) static var lastBody: Data?
    nonisolated(unsafe) static var requestCount = 0

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lastRequest = request
        Self.requestCount += 1
        // URLSession moves httpBody into a stream before it reaches a URLProtocol.
        Self.lastBody = request.httpBody ?? request.httpBodyStream.map(Self.readAll)
        let response = HTTPURLResponse(url: request.url!, statusCode: Self.status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func readAll(_ stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

/// `.serialized` because the stub keeps its state in static properties.
@MainActor
@Suite(.serialized)
struct BackendClientTests {
    private let client: BackendClient = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return BackendClient(baseURL: URL(string: "http://localhost:8081")!, session: URLSession(configuration: configuration))
    }()

    /// A real reply from the backend, shortened.
    private let successBody = Data("""
        {"text":"I was reluctant to leave.","targetLang":"ja",
         "explanation":{"meaning":"The speaker did not want to go.",
                        "words":[{"word":"reluctant","partOfSpeech":"adjective","meaning":"not wanting to do something"}],
                        "translation":"帰るのは気が進まなかった。"},
         "cached":true}
        """.utf8)

    @Test func postsTextAndLanguageCodeToTranslations() async throws {
        StubURLProtocol.status = 200
        StubURLProtocol.body = successBody

        _ = try await client.explain("I was reluctant to leave.", translatingTo: .japanese)

        let request = try #require(StubURLProtocol.lastRequest)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.absoluteString == "http://localhost:8081/api/translations")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        let sent = try JSONSerialization.jsonObject(with: try #require(StubURLProtocol.lastBody)) as? [String: String]
        #expect(sent == ["text": "I was reluctant to leave.", "targetLang": "ja"])
    }

    @Test func decodesTheExplanation() async throws {
        StubURLProtocol.status = 200
        StubURLProtocol.body = successBody

        let explanation = try await client.explain("I was reluctant to leave.", translatingTo: .japanese)

        #expect(explanation.meaning == "The speaker did not want to go.")
        #expect(explanation.words == [.init(word: "reluctant", partOfSpeech: "adjective", meaning: "not wanting to do something")])
        #expect(explanation.translation == "帰るのは気が進まなかった。")
    }

    @Test func repeatRequestIsServedFromTheCacheWithoutNetwork() async throws {
        StubURLProtocol.status = 200
        StubURLProtocol.body = successBody
        StubURLProtocol.requestCount = 0
        var cachedClient = client
        cachedClient.cache = try ResultCache(fileURL: nil)

        let first = try await cachedClient.explain("I was reluctant to leave.", translatingTo: .japanese)
        // The same sentence with other spaces gives the same key.
        let second = try await cachedClient.explain("  I was   reluctant to leave. ", translatingTo: .japanese)

        #expect(StubURLProtocol.requestCount == 1)
        #expect(second == first)
    }

    @Test func otherLanguageIsNotServedFromTheCache() async throws {
        StubURLProtocol.status = 200
        StubURLProtocol.body = successBody
        StubURLProtocol.requestCount = 0
        var cachedClient = client
        cachedClient.cache = try ResultCache(fileURL: nil)

        _ = try await cachedClient.explain("Hello", translatingTo: .japanese)
        _ = try await cachedClient.explain("Hello", translatingTo: .korean)

        #expect(StubURLProtocol.requestCount == 2)
    }

    @Test func failedRequestIsNotCached() async throws {
        StubURLProtocol.status = 502
        StubURLProtocol.body = Data()
        StubURLProtocol.requestCount = 0
        var cachedClient = client
        cachedClient.cache = try ResultCache(fileURL: nil)

        _ = try? await cachedClient.explain("Hello", translatingTo: .vietnamese)
        StubURLProtocol.status = 200
        StubURLProtocol.body = successBody
        _ = try await cachedClient.explain("Hello", translatingTo: .vietnamese)

        #expect(StubURLProtocol.requestCount == 2)
    }

    @Test func serverErrorShowsItsDetail() async {
        StubURLProtocol.status = 502
        StubURLProtocol.body = Data(#"{"title":"Bad Gateway","status":502,"detail":"The translation service is not available. Try again later."}"#.utf8)

        await #expect(throws: BackendError.badStatus(502, detail: "The translation service is not available. Try again later.")) {
            try await client.explain("Hello", translatingTo: .vietnamese)
        }
    }

    @Test func errorWithoutBodyShowsStatus() async {
        StubURLProtocol.status = 500
        StubURLProtocol.body = Data()

        do {
            _ = try await client.explain("Hello", translatingTo: .vietnamese)
            Issue.record("Expected an error")
        } catch {
            #expect(error.localizedDescription == "The server answered with status 500.")
        }
    }

    @Test func unexpectedJSONThrowsDecodingError() async {
        StubURLProtocol.status = 200
        StubURLProtocol.body = Data(#"{"translation":"old shape"}"#.utf8)

        await #expect(throws: DecodingError.self) {
            try await client.explain("Hello", translatingTo: .vietnamese)
        }
    }
}

/// The codes must stay in sync with `TargetLanguage.java` in the backend.
struct TargetLanguageTests {
    @Test func codesMatchTheBackend() {
        #expect(TargetLanguage.allCases.map(\.code) == ["vi", "en", "ja", "ko", "zh", "th", "fr", "es", "de"])
    }
}
