//
//  DictionaryStore.swift
//  EnglishTranslation
//

import Foundation
import SQLite3

/// Things that can go wrong when reading the dictionary file.
enum DictionaryError: Error {
    case fileMissing
    case cannotOpen(String)
    case queryFailed(String)
}

/// Reads the bundled `dictionary.sqlite` (built by the pipeline). Works offline.
///
/// The file is opened read-only. SQLite's default "serialized" mode lets several
/// threads share one connection, so this class is safe to share.
final class DictionaryStore: @unchecked Sendable {
    private var database: OpaquePointer?

    /// Opens the dictionary file at `url`.
    init(url: URL) throws {
        let path = url.path(percentEncoded: false)
        guard sqlite3_open_v2(path, &database, SQLITE_OPEN_READONLY, nil) == SQLITE_OK else {
            let message = Self.message(of: database)
            sqlite3_close(database)
            throw DictionaryError.cannotOpen(message)
        }
    }

    /// Opens the dictionary inside the app bundle.
    static func bundled(in bundle: Bundle = .main) throws -> DictionaryStore {
        guard let url = bundle.url(forResource: "dictionary", withExtension: "sqlite") else {
            throw DictionaryError.fileMissing
        }
        return try DictionaryStore(url: url)
    }

    deinit {
        sqlite3_close(database)
    }

    /// Finds a word by its base form or by a form ("ran" finds "run").
    /// Returns nil when the dictionary does not have it.
    func lookUp(_ word: String) throws -> WordEntry? {
        let key = WordNormalizer.normalize(word)
        guard !key.isEmpty, let match = try findWord(key) else { return nil }
        let senses = try loadSenses(wordID: match.id)
        return WordEntry(word: match.lemma, ipa: match.ipa, senses: senses)
    }

    // MARK: - Queries

    private func findWord(_ key: String) throws -> (id: Int64, lemma: String, ipa: String?)? {
        // UNION ALL keeps the order: a base form is tried before a word form.
        let sql = """
            SELECT id, lemma, ipa FROM words WHERE lemma = ?1 COLLATE NOCASE
            UNION ALL
            SELECT w.id, w.lemma, w.ipa FROM forms f JOIN words w ON w.id = f.word_id
            WHERE f.form = ?1 COLLATE NOCASE
            LIMIT 1
            """
        let rows = try run(sql, text: key) { statement in
            (id: sqlite3_column_int64(statement, 0),
             lemma: Self.text(statement, 1) ?? "",
             ipa: Self.text(statement, 2))
        }
        return rows.first
    }

    private func loadSenses(wordID: Int64) throws -> [Sense] {
        let sql = """
            SELECT pos, cefr, simple_definition, vietnamese, examples
            FROM senses WHERE word_id = ?1 ORDER BY position
            """
        return try run(sql, int: wordID) { statement in
            Sense(
                pos: Self.text(statement, 0) ?? "",
                cefr: Self.text(statement, 1) ?? "",
                simpleDefinition: Self.text(statement, 2) ?? "",
                vietnamese: Self.text(statement, 3) ?? "",
                examples: Self.decodeList(Self.text(statement, 4))
            )
        }
    }

    // MARK: - Small SQLite helpers (private on purpose; TASK-5 has its own)

    private func run<T>(_ sql: String, text: String? = nil, int: Int64? = nil,
                        row: (OpaquePointer) -> T) throws -> [T] {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw DictionaryError.queryFailed(Self.message(of: database))
        }
        defer { sqlite3_finalize(statement) }

        if let text {
            // SQLITE_TRANSIENT makes SQLite copy the string before this call returns.
            let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
            sqlite3_bind_text(statement, 1, text, -1, transient)
        }
        if let int {
            sqlite3_bind_int64(statement, 1, int)
        }

        var rows: [T] = []
        while true {
            let code = sqlite3_step(statement)
            if code == SQLITE_ROW {
                rows.append(row(statement))
            } else if code == SQLITE_DONE {
                return rows
            } else {
                throw DictionaryError.queryFailed(Self.message(of: database))
            }
        }
    }

    private static func text(_ statement: OpaquePointer, _ column: Int32) -> String? {
        guard let pointer = sqlite3_column_text(statement, column) else { return nil }
        return String(cString: pointer)
    }

    /// Reads a JSON list like `["a", "b"]`. A broken value gives an empty list.
    private static func decodeList(_ json: String?) -> [String] {
        guard let data = json?.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([String].self, from: data)) ?? []
    }

    private static func message(of database: OpaquePointer?) -> String {
        guard let database, let pointer = sqlite3_errmsg(database) else { return "unknown error" }
        return String(cString: pointer)
    }
}
