//
//  SenseMediaTests.swift
//  EnglishTranslationTests
//
//  TASK-47 (Sense.imageUrl decoding, DictionaryStore image_url column) and
//  TASK-51 (bundled path vs https URL, the disk cache).
//

import Foundation
import SQLite3
import Testing
@testable import EnglishTranslation

struct SenseMediaTests {

    // MARK: - Sense.imageUrl (TASK-47)

    private static func senseJSON(extra: String) -> Data {
        Data(#"{"pos":"noun","cefr":"A1","simpleDefinition":"d","vietnamese":"v","examples":[]\#(extra)}"#.utf8)
    }

    @Test func senseDecodesImageUrl() throws {
        let sense = try JSONDecoder().decode(Sense.self, from: Self.senseJSON(extra: #","imageUrl":"media/dog_1.webp""#))
        #expect(sense.imageUrl == "media/dog_1.webp")
    }

    @Test func senseDecodesSnakeCaseImageUrl() throws {
        let json = Self.senseJSON(extra: #","image_url":"https://api.loffy.me/media/dog_1_ab12cd34.webp""#)
        let sense = try JSONDecoder().decode(Sense.self, from: json)
        #expect(sense.imageUrl == "https://api.loffy.me/media/dog_1_ab12cd34.webp")
    }

    @Test func senseWithoutImageUrlIsNil() throws {
        let sense = try JSONDecoder().decode(Sense.self, from: Self.senseJSON(extra: ""))
        #expect(sense.imageUrl == nil)
    }

    @Test func senseEncodesImageUrlInCamelCase() throws {
        let sense = Sense(pos: "noun", cefr: "A1", simpleDefinition: "d", vietnamese: "v", examples: [], imageUrl: "media/dog_1.webp")
        let object = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(sense)) as? [String: Any])
        #expect(object["imageUrl"] as? String == "media/dog_1.webp")
        #expect(object["image_url"] == nil)
    }

    @Test func senseWithoutImageUrlEncodesNoKey() throws {
        let sense = Sense(pos: "noun", cefr: "A1", simpleDefinition: "d", vietnamese: "v", examples: [])
        let object = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(sense)) as? [String: Any])
        #expect(object["imageUrl"] == nil)
    }

    // MARK: - DictionaryStore image_url column (TASK-47)

    @Test func dictionaryFileWithImageUrlColumnIsRead() throws {
        let url = Self.temporaryFile(named: "dictionary-image.sqlite")
        defer { try? FileManager.default.removeItem(at: url) }
        try Self.writeDictionary(at: url, withImageColumn: true)

        let entry = try #require(try DictionaryStore(url: url).lookUp("dog"))

        #expect(entry.senses.first?.imageUrl == "media/dog_1.webp")
    }

    @Test func oldDictionaryFileWithoutImageUrlColumnGivesNil() throws {
        let url = Self.temporaryFile(named: "dictionary-no-image.sqlite")
        defer { try? FileManager.default.removeItem(at: url) }
        try Self.writeDictionary(at: url, withImageColumn: false)

        let entry = try #require(try DictionaryStore(url: url).lookUp("dog"))

        #expect(entry.senses.first?.imageUrl == nil)
        #expect(entry.senses.first?.simpleDefinition == "an animal")
    }

    // MARK: - SenseMediaSource (TASK-51)

    @Test func httpsAddressIsRemote() throws {
        let text = "https://api.loffy.me/media/run_1_3fa9c2d1.mp4"
        let source = try #require(SenseMediaSource(path: text))
        #expect(source == .remote(URL(string: text)!))
        #expect(source.isClip)
    }

    @Test func bundledPathsStayBundled() throws {
        #expect(SenseMediaSource(path: "media/run_1.mp4") == .bundled(path: "media/run_1.mp4"))
        #expect(SenseMediaSource(path: "dog_1.webp") == .bundled(path: "dog_1.webp"))
        #expect(try #require(SenseMediaSource(path: "media/run_1.mp4")).isClip)
        #expect(try !#require(SenseMediaSource(path: "dog_1.webp")).isClip)
    }

    @Test func clipDetectionIgnoresTheQueryPartAndCase() throws {
        #expect(try #require(SenseMediaSource(path: "https://x.test/a/RUN.MP4?v=2")).isClip)
        #expect(try !#require(SenseMediaSource(path: "https://x.test/a/dog.webp?name=a.mp4")).isClip)
    }

    @Test func emptyTextAndOtherSchemesGiveNoSource() {
        #expect(SenseMediaSource(path: "") == nil)
        #expect(SenseMediaSource(path: "   ") == nil)
        #expect(SenseMediaSource(path: "http://api.loffy.me/media/a.webp") == nil)
        #expect(SenseMediaSource(path: "ftp://x.test/a.webp") == nil)
        #expect(SenseMediaSource(path: "https://") == nil)
    }

    @Test func bundledFilesOfTheAppAreFound() throws {
        // The app bundles these (TASK-23). Old data may write the path with or without the folder.
        let withFolder = try #require(SenseMediaSource(path: "media/dog_1.webp"))
        let withoutFolder = try #require(SenseMediaSource(path: "dog_1.webp"))
        #expect(withFolder.bundledURL() != nil)
        #expect(withoutFolder.bundledURL() != nil)
        #expect(SenseMediaSource(path: "media/nothing_9.webp")?.bundledURL() == nil)
        #expect(SenseMediaSource.remote(URL(string: "https://x.test/a.webp")!).bundledURL() == nil)
    }

    // MARK: - SenseMediaSource.resolved (bundled words load from the server)

    private static let server = URL(string: "https://api.loffy.me")!

    @Test func bundledPathWithABundledFileUsesTheBundle() throws {
        let source = try #require(SenseMediaSource(path: "media/dog_1.webp"))
        #expect(source.resolved(serverBase: Self.server) == source)
    }

    @Test func bundledPathWithoutABundledFileMapsToTheServer() throws {
        let source = try #require(SenseMediaSource(path: "media/thirst_1.webp"))
        let expected = URL(string: "https://api.loffy.me/media/thirst_1.webp")!
        #expect(source.resolved(serverBase: Self.server) == .remote(expected))

        let clip = try #require(SenseMediaSource(path: "media/fade_1.mp4"))
        #expect(clip.resolved(serverBase: Self.server)?.isClip == true)
        // A path without the folder maps to the same address.
        let bare = try #require(SenseMediaSource(path: "thirst_1.webp"))
        #expect(bare.resolved(serverBase: Self.server) == .remote(expected))
    }

    @Test func serverAddressWithASlashAtTheEndGivesTheSameUrl() throws {
        let source = try #require(SenseMediaSource(path: "media/thirst_1.webp"))
        let base = URL(string: "https://api.loffy.me/")!
        #expect(source.resolved(serverBase: base) == .remote(URL(string: "https://api.loffy.me/media/thirst_1.webp")!))
    }

    @Test func httpsAddressStaysAsItIs() throws {
        let source = try #require(SenseMediaSource(path: "https://cdn.test/media/a.webp"))
        #expect(source.resolved(serverBase: Self.server) == source)
        #expect(source.resolved(serverBase: nil) == source)
    }

    @Test func badValuesGiveNothing() throws {
        #expect(SenseMediaSource(path: "") == nil)
        #expect(SenseMediaSource(path: "http://api.loffy.me/media/a.webp") == nil)
        // A bundled path with no file in the app and no server address cannot be loaded.
        let source = try #require(SenseMediaSource(path: "media/thirst_1.webp"))
        #expect(source.resolved(serverBase: nil) == nil)
        // A path with no file name at all.
        let noName = try #require(SenseMediaSource(path: "media/"))
        #expect(noName.resolved(serverBase: Self.server) == nil)
    }

    // MARK: - MediaFileCache (TASK-51)

    /// Counts how many times the fake download ran.
    private final class Counter: @unchecked Sendable {
        private let lock = NSLock()
        private var value = 0
        var count: Int { lock.withLock { value } }
        func increase() { lock.withLock { value += 1 } }
    }

    /// A cache folder whose name has a space, like "Application Support" (see swift.md).
    private static func makeFolder() -> URL {
        FileManager.default.temporaryDirectory.appending(path: "media cache \(UUID().uuidString)")
    }

    @Test func secondRequestUsesTheSavedFileWithoutTheNetwork() async throws {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let calls = Counter()
        let cache = MediaFileCache(directory: folder, maxBytes: 1_000) { _ in
            calls.increase()
            return Data("photo".utf8)
        }
        let url = URL(string: "https://api.loffy.me/media/dog_1_ab12cd34.webp")!

        let first = try await cache.file(for: url)
        let second = try await cache.file(for: url)

        #expect(first == second)
        #expect(first.pathExtension == "webp")
        #expect(try Data(contentsOf: first) == Data("photo".utf8))
        #expect(calls.count == 1)
    }

    @Test func savedFileSurvivesWhenTheNetworkIsOff() async throws {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = URL(string: "https://api.loffy.me/media/dog_1_ab12cd34.webp")!
        let online = MediaFileCache(directory: folder, maxBytes: 1_000) { _ in Data("photo".utf8) }
        _ = try await online.file(for: url)

        // A new cache object on the same folder, as after an app restart, with the network off.
        let offline = MediaFileCache(directory: folder, maxBytes: 1_000) { _ in throw URLError(.notConnectedToInternet) }
        let file = try await offline.file(for: url)

        #expect(try Data(contentsOf: file) == Data("photo".utf8))
    }

    @Test func failedDownloadThrowsAndSavesNothing() async throws {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let cache = MediaFileCache(directory: folder, maxBytes: 1_000) { _ in throw URLError(.notConnectedToInternet) }
        let url = URL(string: "https://api.loffy.me/media/dog_1_ab12cd34.webp")!

        await #expect(throws: URLError.self) { try await cache.file(for: url) }

        #expect(await cache.cachedFile(for: url) == nil)
        #expect(await cache.totalBytes() == 0)
    }

    @Test func emptyAnswerIsNotSaved() async {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let cache = MediaFileCache(directory: folder, maxBytes: 1_000) { _ in Data() }
        let url = URL(string: "https://api.loffy.me/media/a.webp")!

        await #expect(throws: MediaFileCache.CacheError.self) { try await cache.file(for: url) }
    }

    @Test func oldestFilesAreRemovedToStayUnderTheLimit() async throws {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        // Limit 120 bytes; each file is 30 bytes, so only four fit.
        let cache = MediaFileCache(directory: folder, maxBytes: 120) { _ in Data(repeating: 1, count: 30) }
        let urls = ["a", "b", "c", "d", "e"].map { URL(string: "https://x.test/\($0).webp")! }

        for (index, url) in urls.enumerated() {
            let file = try await cache.file(for: url)
            // Give each file a clear age: "a" is the oldest.
            try FileManager.default.setAttributes(
                [.modificationDate: Date(timeIntervalSinceNow: Double(index - 10) * 100)],
                ofItemAtPath: file.path(percentEncoded: false))
        }

        #expect(await cache.cachedFile(for: urls[0]) == nil)  // the oldest one is gone
        #expect(await cache.cachedFile(for: urls[1]) != nil)
        #expect(await cache.cachedFile(for: urls[4]) != nil)
        #expect(await cache.totalBytes() <= 120)
    }

    @Test func fileBiggerThanAQuarterOfTheLimitIsNotSaved() async {
        let folder = Self.makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let cache = MediaFileCache(directory: folder, maxBytes: 100) { _ in Data(repeating: 1, count: 50) }
        let url = URL(string: "https://x.test/big.mp4")!

        await #expect(throws: MediaFileCache.CacheError.self) { try await cache.file(for: url) }
        #expect(await cache.cachedFile(for: url) == nil)
    }

    // MARK: - Helpers

    private static func temporaryFile(named name: String) -> URL {
        FileManager.default.temporaryDirectory.appending(path: "\(UUID().uuidString)-\(name)")
    }

    /// A tiny dictionary file with "dog". `withImageColumn` adds the `senses.image_url` column (TASK-23).
    private static func writeDictionary(at url: URL, withImageColumn: Bool) throws {
        var db: OpaquePointer?
        guard sqlite3_open(url.path(percentEncoded: false), &db) == SQLITE_OK else { throw DictionaryError.fileMissing }
        defer { sqlite3_close(db) }
        let imageColumn = withImageColumn ? ", image_url TEXT" : ""
        let imageValue = withImageColumn ? ", 'media/dog_1.webp'" : ""
        let sql = """
            CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL, ipa TEXT);
            CREATE TABLE forms (form TEXT NOT NULL, word_id INTEGER NOT NULL);
            CREATE TABLE senses (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, position INTEGER NOT NULL,
                pos TEXT NOT NULL, cefr TEXT NOT NULL, simple_definition TEXT NOT NULL, vietnamese TEXT NOT NULL,
                examples TEXT NOT NULL, usage TEXT NOT NULL\(imageColumn));
            INSERT INTO words VALUES (1, 'dog', 1, NULL);
            INSERT INTO senses VALUES (1, 1, 0, 'noun', 'A1', 'an animal', 'con chó', '["A dog."]', '[]'\(imageValue));
            """
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw DictionaryError.queryFailed("setup") }
    }
}
