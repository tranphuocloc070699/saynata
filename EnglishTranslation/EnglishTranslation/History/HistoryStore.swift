//
//  HistoryStore.swift
//  EnglishTranslation
//

import Foundation
import SQLite3

/// The list of past results on this device. `HistoryStore` is the real one; tests use a fake.
protocol HistoryStoring: Sendable {
    /// Adds an item at the top. The same item again moves to the top instead of making a second row.
    func record(_ item: SavedItem) async
    /// Newest first.
    func items() async -> [SavedItem]
    func remove(id: String) async
    func removeAll() async
}

/// History in a small SQLite file in the App Group folder, next to `ResultCache`, so the Share and Translate
/// extensions add to the same list as the app (TASK-93.2). Only the newest `limit` items are kept.
actor HistoryStore: HistoryStoring {
    /// The file of the app (and of the extensions, when the App Group works).
    /// Nil if the file cannot be opened: the app then works without History.
    /// Also nil inside unit tests: the tests run in the app on the simulator, and their made-up words and
    /// sentences would otherwise show up in the real History list.
    static let shared: HistoryStore? = {
        guard ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil else { return nil }
        let folder = ResultCache.cacheFolder()
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return try? HistoryStore(fileURL: folder.appending(path: "history.sqlite"))
    }()

    /// How many items History keeps.
    static let defaultLimit = 1000

    private let database: OpaquePointer
    private let limit: Int

    /// Opens (or creates) the file. Pass `nil` for a list that lives only in memory (tests).
    init(fileURL: URL?, limit: Int = HistoryStore.defaultLimit) throws {
        var handle: OpaquePointer?
        let path = fileURL?.path(percentEncoded: false) ?? ":memory:"
        guard sqlite3_open(path, &handle) == SQLITE_OK, let handle else {
            sqlite3_close(handle)
            throw ResultCacheError.cannotOpen(path)
        }
        // The app and an extension may use the file at the same time: wait up to 2 s instead of failing.
        sqlite3_busy_timeout(handle, 2000)
        let createTable = """
            CREATE TABLE IF NOT EXISTS history (
                item_key  TEXT PRIMARY KEY,
                kind      TEXT NOT NULL,
                text      TEXT NOT NULL,
                preview   TEXT NOT NULL,
                chip      TEXT,
                used_at   REAL NOT NULL
            )
            """
        guard sqlite3_exec(handle, createTable, nil, nil, nil) == SQLITE_OK else {
            sqlite3_close(handle)
            throw ResultCacheError.cannotOpen(path)
        }
        database = handle
        self.limit = limit
    }

    deinit {
        sqlite3_close(database)
    }

    func record(_ item: SavedItem) {
        // REPLACE deletes the old row and adds a new one, so a repeat also gets a new (higher) rowid.
        let sql = "INSERT OR REPLACE INTO history (item_key, kind, text, preview, chip, used_at) VALUES (?, ?, ?, ?, ?, ?)"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_text(statement, 1, item.id, -1, Self.copyBytes)
        sqlite3_bind_text(statement, 2, item.kind.rawValue, -1, Self.copyBytes)
        sqlite3_bind_text(statement, 3, item.text, -1, Self.copyBytes)
        sqlite3_bind_text(statement, 4, item.preview, -1, Self.copyBytes)
        if let chip = item.chip {
            sqlite3_bind_text(statement, 5, chip, -1, Self.copyBytes)
        } else {
            sqlite3_bind_null(statement, 5)
        }
        sqlite3_bind_double(statement, 6, item.date.timeIntervalSince1970)
        _ = sqlite3_step(statement)
        trim()
    }

    func items() -> [SavedItem] {
        let sql = "SELECT kind, text, preview, chip, used_at FROM history ORDER BY used_at DESC, rowid DESC"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(statement) }

        var result: [SavedItem] = []
        while sqlite3_step(statement) == SQLITE_ROW {
            guard let kind = SavedItem.Kind(rawValue: Self.text(statement, 0) ?? "") else { continue }
            result.append(SavedItem(
                kind: kind,
                text: Self.text(statement, 1) ?? "",
                preview: Self.text(statement, 2) ?? "",
                chip: Self.text(statement, 3),
                date: Date(timeIntervalSince1970: sqlite3_column_double(statement, 4))))
        }
        return result
    }

    func remove(id: String) {
        run("DELETE FROM history WHERE item_key = ?", binding: id)
    }

    func removeAll() {
        run("DELETE FROM history")
    }

    /// Keeps only the newest `limit` rows.
    private func trim() {
        let sql = """
            DELETE FROM history WHERE item_key NOT IN (
                SELECT item_key FROM history ORDER BY used_at DESC, rowid DESC LIMIT ?)
            """
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_int(statement, 1, Int32(limit))
        _ = sqlite3_step(statement)
    }

    private func run(_ sql: String, binding value: String? = nil) {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        if let value { sqlite3_bind_text(statement, 1, value, -1, Self.copyBytes) }
        _ = sqlite3_step(statement)
    }

    private static func text(_ statement: OpaquePointer?, _ column: Int32) -> String? {
        sqlite3_column_text(statement, column).map { String(cString: $0) }
    }

    /// Tells SQLite to copy the bytes we pass, because Swift may free them right after the call.
    private static let copyBytes = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
}
