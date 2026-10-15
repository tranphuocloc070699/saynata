//
//  AccountTestSupport.swift
//  EnglishTranslationTests
//
//  Fakes shared by the account tests (TASK-70): a Keychain in memory, and a fake server that answers
//  requests with a script. Each `ScriptedServer` has its own host name, so tests never share state
//  and can run at the same time.
//

import Foundation
@testable import EnglishTranslation

/// A Keychain in memory. Remembers every write, so a test can check what was saved.
final class FakeSecretStore: SecretStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String: String] = [:]

    init(_ values: [String: String] = [:]) {
        self.values = values
    }

    func secret(forKey key: String) -> String? {
        lock.withLock { values[key] }
    }

    func setSecret(_ value: String?, forKey key: String) throws {
        lock.withLock { values[key] = value }
    }

    /// Everything saved, for assertions.
    var all: [String: String] { lock.withLock { values } }
}

/// One request the fake server got.
struct ScriptedRequest {
    let method: String
    let path: String
    let authorization: String?
    let body: Data

    /// The JSON body as a dictionary, or empty.
    var json: [String: Any] {
        (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
    }
}

/// What the fake server answers.
struct ScriptedReply {
    var status = 200
    var headers: [String: String] = [:]
    var body = Data()

    static func json(_ text: String, status: Int = 200, headers: [String: String] = [:]) -> ScriptedReply {
        ScriptedReply(status: status, headers: headers, body: Data(text.utf8))
    }

    static func empty(_ status: Int = 204) -> ScriptedReply {
        ScriptedReply(status: status)
    }

    /// A Spring error body with a `code`, like `{"detail": "...", "code": "rate_limited"}`.
    static func problem(_ status: Int, code: String?, detail: String = "message", headers: [String: String] = [:]) -> ScriptedReply {
        let codePart = code.map { ",\"code\":\"\($0)\"" } ?? ""
        return .json("{\"detail\":\"\(detail)\"\(codePart)}", status: status, headers: headers)
    }
}

/// A fake backend. `handler` gets each request and returns the reply. Use `session` and `baseURL` for a client.
final class ScriptedServer: @unchecked Sendable {
    let baseURL: URL
    let session: URLSession
    private let lock = NSLock()
    private var recorded: [ScriptedRequest] = []
    /// Called for every request. The default answers 500.
    var handler: (ScriptedRequest) -> ScriptedReply = { _ in .empty(500) }

    private static let registryLock = NSLock()
    nonisolated(unsafe) private static var servers: [String: ScriptedServer] = [:]

    init() {
        let host = "t\(UUID().uuidString.prefix(8).lowercased()).test"
        baseURL = URL(string: "http://\(host)")!
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ScriptedURLProtocol.self]
        session = URLSession(configuration: configuration)
        Self.registryLock.withLock { Self.servers[host] = self }
    }

    deinit {
        let host = baseURL.host ?? ""
        Self.registryLock.withLock { _ = Self.servers.removeValue(forKey: host) }
    }

    static func server(forHost host: String?) -> ScriptedServer? {
        registryLock.withLock { host.flatMap { servers[$0] } }
    }

    /// Every request so far, in order.
    var requests: [ScriptedRequest] { lock.withLock { recorded } }

    /// The requests whose path is `path`.
    func requests(to path: String) -> [ScriptedRequest] {
        requests.filter { $0.path == path }
    }

    fileprivate func answer(_ request: ScriptedRequest) -> ScriptedReply {
        lock.withLock { recorded.append(request) }
        return handler(request)
    }
}

final class ScriptedURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let server = ScriptedServer.server(forHost: request.url?.host) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
            return
        }
        let scripted = ScriptedRequest(
            method: request.httpMethod ?? "GET",
            path: request.url?.path ?? "",
            authorization: request.value(forHTTPHeaderField: "Authorization"),
            // URLSession moves httpBody into a stream before it reaches a URLProtocol.
            body: request.httpBody ?? request.httpBodyStream.map(Self.readAll) ?? Data())
        let reply = server.answer(scripted)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: reply.status, httpVersion: nil, headerFields: reply.headers)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: reply.body)
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

// MARK: - Sample JSON

/// A token reply like the backend's, with the given tokens.
func tokenReply(access: String, refresh: String, email: String = "learner@example.com") -> ScriptedReply {
    .json("""
        {"accessToken":"\(access)","refreshToken":"\(refresh)","expiresIn":3600,"user":{"id":7,"email":"\(email)"}}
        """)
}

/// A session saved in the (fake) Keychain, as `AccountStore` saves it.
func savedSessionJSON(access: String, refresh: String, email: String = "learner@example.com") -> String {
    """
    {"accessToken":"\(access)","refreshToken":"\(refresh)","user":{"id":7,"email":"\(email)"}}
    """
}

/// The body of a good `POST /api/translations` reply.
let translationReplyJSON = """
    {"text":"Hello there.","targetLang":"vi",
     "explanation":{"meaning":"A friendly greeting.","words":[],"translation":"Xin chào."},"cached":false}
    """

/// The body of a good `GET /api/words/{word}` reply.
let wordReplyJSON = """
    {"word":"serendipity","cached":false,
     "entry":{"senses":[{"pos":"noun","cefr":"C2","simpleDefinition":"a lucky thing you find by chance",
                         "vietnamese":"sự tình cờ may mắn","examples":["Meeting her was pure serendipity."],
                         "usage":[{"kind":"phrase","pattern":"by serendipity","example":"We met by serendipity."}]}]}}
    """
