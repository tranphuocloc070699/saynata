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
/// The file is opened read-only with SQLITE_OPEN_FULLMUTEX. Apple's SQLite is built in
/// multi-thread mode, so this flag is what makes sharing one connection across threads safe.
/// See https://www.sqlite.org/threadsafe.html
final class DictionaryStore: @unchecked Sendable {
    private var database: OpaquePointer?
    /// The old bundled file has no `senses.usage` column (added by TASK-24). Then usage is empty.
    private var hasUsageColumn = false
    /// The old bundled file has no `forms.type` column (added by TASK-35). Then the type is "form".
    private var hasFormTypeColumn = false
    /// Added by TASK-23 (media pass). Then imageUrl is filled.
    private var hasImageUrlColumn = false

    /// Opens the dictionary file at `url`.
    init(url: URL) throws {
        let path = url.path(percentEncoded: false)
        guard sqlite3_open_v2(path, &database, SQLITE_OPEN_READONLY | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else {
            let message = Self.message(of: database)
            sqlite3_close(database)
            throw DictionaryError.cannotOpen(message)
        }
        hasUsageColumn = try columnExists("usage", in: "senses")
        hasFormTypeColumn = try columnExists("type", in: "forms")
        hasImageUrlColumn = try columnExists("image_url", in: "senses")
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
        let key = CacheKeys.normalizeWord(word)
        guard !key.isEmpty, let match = try findWord(key) else { return nil }
        let senses = try loadSenses(wordID: match.id)
        return WordEntry(word: match.lemma, ipa: match.ipa, senses: senses, matchedForm: match.matchedForm)
    }

    /// Every spelling the dictionary knows, for "did you mean". Base words and word forms ("ran"),
    /// each with the base word to show and its NGSL rank (lower = more common).
    func spellingCandidates() throws -> [SpellingCandidate] {
        let sql = """
            SELECT lemma, lemma, rank FROM words
            UNION ALL
            SELECT f.form, w.lemma, w.rank FROM forms f JOIN words w ON w.id = f.word_id
            """
        return try run(sql) { statement in
            SpellingCandidate(
                spelling: (Self.text(statement, 0) ?? "").lowercased(),
                lemma: Self.text(statement, 1) ?? "",
                rank: Int(sqlite3_column_int64(statement, 2)))
        }
    }

    // MARK: - Queries

    private func findWord(_ key: String) throws -> (id: Int64, lemma: String, ipa: String?, matchedForm: MatchedForm?)? {
        // UNION ALL keeps the order: a base form is tried before a word form.
        // Without the type column, SELECT NULL so the column numbers stay the same.
        let typeColumn = hasFormTypeColumn ? "f.type" : "NULL"
        let sql = """
            SELECT id, lemma, ipa, NULL, NULL FROM words WHERE lemma = ?1 COLLATE NOCASE
            UNION ALL
            SELECT w.id, w.lemma, w.ipa, f.form, \(typeColumn) FROM forms f JOIN words w ON w.id = f.word_id
            WHERE f.form = ?1 COLLATE NOCASE
            LIMIT 1
            """
        let rows = try run(sql, text: key) { statement in
            let form = Self.text(statement, 3)
            return (id: sqlite3_column_int64(statement, 0),
                    lemma: Self.text(statement, 1) ?? "",
                    ipa: Self.text(statement, 2),
                    matchedForm: form.map { MatchedForm(text: $0.lowercased(), type: Self.text(statement, 4) ?? "form") })
        }
        return rows.first
    }

    private func loadSenses(wordID: Int64) throws -> [Sense] {
        // Without the column, SELECT NULL so the column numbers stay the same.
        let usageColumn = hasUsageColumn ? "usage" : "NULL"
        let imageColumn = hasImageUrlColumn ? "image_url" : "NULL"
        let sql = """
            SELECT pos, cefr, simple_definition, vietnamese, examples, \(usageColumn), \(imageColumn)
            FROM senses WHERE word_id = ?1 ORDER BY position
            """
        return try run(sql, int: wordID) { statement in
            Sense(
                pos: Self.text(statement, 0) ?? "",
                cefr: Self.text(statement, 1) ?? "",
                simpleDefinition: Self.text(statement, 2) ?? "",
                vietnamese: Self.text(statement, 3) ?? "",
                examples: Self.decodeExamples(Self.text(statement, 4)),
                usage: Self.decodeUsage(Self.text(statement, 5)),
                imageUrl: Self.text(statement, 6)
            )
        }
    }

    private func columnExists(_ column: String, in table: String) throws -> Bool {
        // PRAGMA cannot bind a table name, so it is written into the SQL. Callers pass fixed names only.
        let names = try run("PRAGMA table_info(\(table))") { statement in Self.text(statement, 1) }
        return names.contains(column)
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
    /// Items can be plain strings (old data) or `{ level, text }` objects.
    static func decodeExamples(_ json: String?) -> [Example] {
        guard let data = json?.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([Example].self, from: data)) ?? []
    }

    /// Reads the usage JSON `[{"kind": "idiom", "pattern": "...", "example": "..."}]`.
    /// A missing value or broken JSON gives an empty list. A missing or unknown kind becomes `.pattern`.
    static func decodeUsage(_ json: String?) -> [Usage] {
        guard let data = json?.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([Usage].self, from: data)) ?? []
    }

    private static func message(of database: OpaquePointer?) -> String {
        guard let database, let pointer = sqlite3_errmsg(database) else { return "unknown error" }
        return String(cString: pointer)
    }
}
