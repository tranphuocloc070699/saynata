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
    /// The cache file in Application Support. Nil if the file cannot be opened (the app then works without a cache).
    static let shared: ResultCache? = {
        let folder = URL.applicationSupportDirectory
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return try? ResultCache(fileURL: folder.appending(path: "result-cache.sqlite"))
    }()

    private let database: OpaquePointer

    /// Opens (or creates) the cache file. Pass `nil` for a cache that lives only in memory (tests).
    init(fileURL: URL?) throws {
        var handle: OpaquePointer?
        let path = fileURL?.path(percentEncoded: false) ?? ":memory:"
        guard sqlite3_open(path, &handle) == SQLITE_OK, let handle else {
            sqlite3_close(handle)
            throw ResultCacheError.cannotOpen(path)
        }
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
