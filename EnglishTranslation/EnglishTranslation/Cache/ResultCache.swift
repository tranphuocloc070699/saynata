//
//  ResultCache.swift
//  EnglishTranslation
//

import Foundation
import SQLite3

/// A small SQLite file on the phone with the backend answers we already got.
/// A repeat request with the same key is answered from here, with no network call (spec R2).
///
/// The backend JSON is stored as-is (a blob), so a change of the Swift models does not need a migration.
actor ResultCache {
    /// The App Group id. It must be the same in both `.entitlements` files (app and share extension).
    static let appGroupID = "group.com.luke.EnglishTranslation"

    /// The cache file, shared by the app and the share extension when the App Group works.
    /// Nil if the file cannot be opened (the app then works without a cache).
    static let shared: ResultCache? = {
        let folder = cacheFolder()
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let file = folder.appending(path: "result-cache.sqlite")
        moveOldCacheIfNeeded(to: file)
        return try? ResultCache(fileURL: file)
    }()

    /// The App Group folder. If the group is not set up (for example no signing team yet), the process's own
    /// Application Support folder: the app and the extension then keep two separate caches, and both still work.
    /// The Mac app has no extension to share with, and asking macOS for a group folder it is not entitled to shows a
    /// privacy prompt, so it always uses its own (sandboxed) Application Support folder.
    static func cacheFolder() -> URL {
        #if os(macOS)
        URL.applicationSupportDirectory
        #else
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)
            ?? URL.applicationSupportDirectory
        #endif
    }

    /// Before the share extension, the app kept the cache in Application Support. Move it to the group
    /// folder once, so saved answers are not lost. Does nothing when both folders are the same.
    private static func moveOldCacheIfNeeded(to file: URL) {
        let old = URL.applicationSupportDirectory.appending(path: "result-cache.sqlite")
        let manager = FileManager.default
        guard old != file,
              manager.fileExists(atPath: old.path(percentEncoded: false)),
              !manager.fileExists(atPath: file.path(percentEncoded: false))
        else { return }
        try? manager.moveItem(at: old, to: file)
    }

    private let database: OpaquePointer

    /// Opens (or creates) the cache file. Pass `nil` for a cache that lives only in memory (tests).
    init(fileURL: URL?) throws {
        var handle: OpaquePointer?
        let path = fileURL?.path(percentEncoded: false) ?? ":memory:"
        guard sqlite3_open(path, &handle) == SQLITE_OK, let handle else {
            sqlite3_close(handle)
            throw ResultCacheError.cannotOpen(path)
        }
        // The app and the share extension may use the file at the same time: wait up to 2 s instead of failing.
        sqlite3_busy_timeout(handle, 2000)
        let createTable = """
            CREATE TABLE IF NOT EXISTS result_cache (
                cache_key  TEXT PRIMARY KEY,
                kind       TEXT NOT NULL,
                data       BLOB NOT NULL,
                created_at REAL NOT NULL
            )
            """
        guard sqlite3_exec(handle, createTable, nil, nil, nil) == SQLITE_OK else {
            sqlite3_close(handle)
            throw ResultCacheError.cannotOpen(path)
        }
        database = handle
    }

    deinit {
        sqlite3_close(database)
    }

    /// The stored backend JSON for `key`, or nil if there is none.
    func data(forKey key: String) -> Data? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, "SELECT data FROM result_cache WHERE cache_key = ?", -1, &statement, nil) == SQLITE_OK
        else { return nil }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_text(statement, 1, key, -1, Self.copyBytes)

        guard sqlite3_step(statement) == SQLITE_ROW else { return nil }
        let count = Int(sqlite3_column_bytes(statement, 0))
        guard count > 0, let bytes = sqlite3_column_blob(statement, 0) else { return Data() }
        return Data(bytes: bytes, count: count)
    }

    /// Saves `data` under `key`. An older answer with the same key is replaced.
    /// A failed save is ignored: the cache is a speed-up, never a reason to fail a request.
    func save(_ data: Data, forKey key: String, kind: CacheKeys.Kind, createdAt: Date = .now) {
        let sql = "INSERT OR REPLACE INTO result_cache (cache_key, kind, data, created_at) VALUES (?, ?, ?, ?)"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_text(statement, 1, key, -1, Self.copyBytes)
        sqlite3_bind_text(statement, 2, kind.rawValue, -1, Self.copyBytes)
        data.withUnsafeBytes { buffer in
            _ = sqlite3_bind_blob(statement, 3, buffer.baseAddress, Int32(buffer.count), Self.copyBytes)
        }
        sqlite3_bind_double(statement, 4, createdAt.timeIntervalSince1970)
        _ = sqlite3_step(statement)
    }

    /// Tells SQLite to copy the bytes we pass, because Swift may free them right after the call.
    private static let copyBytes = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
}

enum ResultCacheError: Error {
    case cannotOpen(String)
}
