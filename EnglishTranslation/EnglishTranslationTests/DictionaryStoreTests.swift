//
//  DictionaryStoreTests.swift
//  EnglishTranslationTests
//

import Foundation
import SQLite3
import Testing
@testable import EnglishTranslation

/// These tests read the real `dictionary.sqlite` that is bundled in the app.
struct DictionaryStoreTests {
    @Test func bundledDictionaryOpens() throws {
        _ = try DictionaryStore.bundled()
    }

    @Test func knownWordIsFound() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("run"))
        #expect(entry.word == "run")
        #expect(entry.ipa != nil)
        let first = try #require(entry.senses.first)
        #expect(first.pos == "verb")
        #expect(!first.simpleDefinition.isEmpty)
        #expect(!first.vietnamese.isEmpty)
        #expect(first.examples.count == 2)
        #expect(!first.usage.isEmpty)  // filled by the TASK-24 usage pass
    }

    @Test func wordFormFindsTheBaseWord() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("ran"))
        #expect(entry.word == "run")
    }

    @Test func inputIsNormalisedBeforeLookup() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("  RUN \n"))
        #expect(entry.word == "run")
    }

    @Test func unknownWordIsNil() throws {
        let store = try DictionaryStore.bundled()
        #expect(try store.lookUp("zzzzqq") == nil)
        #expect(try store.lookUp("   ") == nil)
    }

    @Test func usageKindDefaultsToPattern() throws {
        let json = #"{"pattern": "run out of", "example": "We ran out of milk."}"#
        let usage = try JSONDecoder().decode(Usage.self, from: Data(json.utf8))
        #expect(usage.kind == .pattern)
        let idiom = #"{"kind": "idiom", "pattern": "a", "example": "b"}"#
        #expect(try JSONDecoder().decode(Usage.self, from: Data(idiom.utf8)).kind == .idiom)
    }

    @Test func usageJsonFromTheExportIsDecoded() {
        let json = """
            [{"kind": "collocation", "pattern": "make a decision", "example": "We need to make a decision today."},
             {"kind": "weird", "pattern": "depend on + something", "example": "It depends on the weather."}]
            """
        let usage = DictionaryStore.decodeUsage(json)
        #expect(usage.count == 2)
        #expect(usage[0] == Usage(kind: .collocation, pattern: "make a decision", example: "We need to make a decision today."))
        #expect(usage[1].kind == .pattern)  // unknown kind
        #expect(DictionaryStore.decodeUsage(nil).isEmpty)
        #expect(DictionaryStore.decodeUsage("not json").isEmpty)
        #expect(DictionaryStore.decodeUsage("[]").isEmpty)
    }

    @Test func fileWithUsageColumnIsRead() throws {
        let url = FileManager.default.temporaryDirectory.appending(path: "dictionary-usage-\(UUID().uuidString).sqlite")
        defer { try? FileManager.default.removeItem(at: url) }
        try Self.writeTinyDictionary(at: url)

        let entry = try #require(try DictionaryStore(url: url).lookUp("depend"))

        #expect(entry.senses.first?.usage == [
            Usage(kind: .pattern, pattern: "depend on + something", example: "It depends on the weather."),
        ])
    }

    @Test func fileWithLevelsAndFormTypesIsRead() throws {
        let url = FileManager.default.temporaryDirectory.appending(path: "dictionary-forms-\(UUID().uuidString).sqlite")
        defer { try? FileManager.default.removeItem(at: url) }
        try Self.writeTinyDictionary(at: url, withFormTypes: true)

        let store = try DictionaryStore(url: url)
        let ran = try #require(try store.lookUp("Ran"))

        #expect(ran.word == "run")
        #expect(ran.matchedForm == WordForm(form: "ran", type: "past tense"))
        #expect(ran.senses.first?.examples == [
            Example(level: .easy, text: "I run."),
            Example(level: .medium, text: "She runs to school."),
            Example(level: .harder, text: "He ran the shop for ten years."),
        ])
        // The base word has no matched form.
        #expect(try store.lookUp("run")?.matchedForm == nil)
    }

    @Test func oldFileWithoutFormTypesStillLoads() throws {
        let url = FileManager.default.temporaryDirectory.appending(path: "dictionary-old-\(UUID().uuidString).sqlite")
        defer { try? FileManager.default.removeItem(at: url) }
        try Self.writeTinyDictionary(at: url, withFormTypes: false)

        let ran = try #require(try DictionaryStore(url: url).lookUp("ran"))

        #expect(ran.word == "run")
        #expect(ran.matchedForm == WordForm(form: "ran", type: "form"))  // no type column: the neutral label
        // Old examples are plain strings. They become easy, medium.
        #expect(ran.senses.first?.examples.map(\.level) == [.easy, .medium])
    }

    @Test func plainStringExamplesBecomeEasyThenMedium() throws {
        let old = #"{"pos":"verb","cefr":"A1","simpleDefinition":"d","vietnamese":"v","examples":["One.","Two."]}"#
        let sense = try JSONDecoder().decode(Sense.self, from: Data(old.utf8))
        #expect(sense.examples == [Example(level: .easy, text: "One."), Example(level: .medium, text: "Two.")])

        let new = #"{"pos":"verb","cefr":"A1","simpleDefinition":"d","vietnamese":"v","examples":[{"level":"harder","text":"Three."}]}"#
        let newer = try JSONDecoder().decode(Sense.self, from: Data(new.utf8))
        #expect(newer.examples == [Example(level: .harder, text: "Three.")])
    }

    /// A tiny dictionary file with the new `usage` column, made with the schema the pipeline exports.
    private static func writeTinyDictionary(at url: URL, withFormTypes: Bool? = nil) throws {
        var db: OpaquePointer?
        guard sqlite3_open(url.path(percentEncoded: false), &db) == SQLITE_OK else { throw DictionaryError.fileMissing }
        defer { sqlite3_close(db) }
        if let withFormTypes {
            try writeRunDictionary(db, withFormTypes: withFormTypes)
            return
        }
        let sql = """
            CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL, ipa TEXT);
            CREATE TABLE forms (form TEXT NOT NULL, word_id INTEGER NOT NULL);
            CREATE TABLE senses (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, position INTEGER NOT NULL,
                pos TEXT NOT NULL, cefr TEXT NOT NULL, simple_definition TEXT NOT NULL, vietnamese TEXT NOT NULL,
                examples TEXT NOT NULL, usage TEXT NOT NULL, image_type TEXT NOT NULL, image_query TEXT NOT NULL);
            INSERT INTO words VALUES (1, 'depend', 1, NULL);
            INSERT INTO senses VALUES (1, 1, 0, 'verb', 'A2', 'to be decided by something', 'phụ thuộc',
                '["It depends."]',
                '[{"kind":"pattern","pattern":"depend on + something","example":"It depends on the weather."}]',
                'none', '');
            """
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw DictionaryError.queryFailed("setup") }
    }

    /// "run" with the form "ran". The new file has levelled examples and `forms.type`; the old one has neither.
    private static func writeRunDictionary(_ db: OpaquePointer?, withFormTypes: Bool) throws {
        let formsTable = withFormTypes
            ? "CREATE TABLE forms (form TEXT NOT NULL, word_id INTEGER NOT NULL, type TEXT NOT NULL);"
            : "CREATE TABLE forms (form TEXT NOT NULL, word_id INTEGER NOT NULL);"
        let formRow = withFormTypes ? "INSERT INTO forms VALUES ('ran', 1, 'past tense');" : "INSERT INTO forms VALUES ('ran', 1);"
        let examples = withFormTypes
            ? #"[{"level":"easy","text":"I run."},{"level":"medium","text":"She runs to school."},{"level":"harder","text":"He ran the shop for ten years."}]"#
            : #"["I run.", "She runs to school."]"#
        let sql = """
            CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL, ipa TEXT);
            \(formsTable)
            CREATE TABLE senses (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, position INTEGER NOT NULL,
                pos TEXT NOT NULL, cefr TEXT NOT NULL, simple_definition TEXT NOT NULL, vietnamese TEXT NOT NULL,
                examples TEXT NOT NULL, usage TEXT NOT NULL, image_type TEXT NOT NULL, image_query TEXT NOT NULL);
            INSERT INTO words VALUES (1, 'run', 1, NULL);
            \(formRow)
            INSERT INTO senses VALUES (1, 1, 0, 'verb', 'A1', 'to move fast on foot', 'chạy', '\(examples)', '[]', 'none', '');
            """
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw DictionaryError.queryFailed("setup") }
    }
}
