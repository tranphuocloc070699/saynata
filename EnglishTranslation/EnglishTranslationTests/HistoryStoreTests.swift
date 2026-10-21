//
//  HistoryStoreTests.swift
//  EnglishTranslationTests
//
//  TASK-93.2: the History list on the device (order, repeats, the 1000 limit), the day groups, and the search.
//

import Foundation
import Testing
@testable import EnglishTranslation

struct HistoryStoreTests {
    private func word(_ text: String, at seconds: TimeInterval = 0, preview: String = "meaning") -> SavedItem {
        SavedItem(kind: .word, text: text, preview: preview, chip: "noun · A1", date: Date(timeIntervalSince1970: seconds))
    }

    @Test func newestItemIsFirst() async throws {
        let history = try HistoryStore(fileURL: nil)
        await history.record(word("apple", at: 1))
        await history.record(word("pear", at: 2))
        #expect(await history.items().map(\.text) == ["pear", "apple"])
    }

    @Test func aRepeatMovesToTheTopAndMakesNoSecondRow() async throws {
        let history = try HistoryStore(fileURL: nil)
        await history.record(word("apple", at: 1))
        await history.record(word("pear", at: 2))
        await history.record(word("Apple", at: 3, preview: "newer meaning"))

        let items = await history.items()
        #expect(items.map(\.text) == ["Apple", "pear"])
        #expect(items.first?.preview == "newer meaning")
    }

    @Test func aWordAndASentenceWithTheSameTextAreTwoItems() async throws {
        let history = try HistoryStore(fileURL: nil)
        await history.record(SavedItem(kind: .word, text: "run", preview: "a"))
        await history.record(SavedItem(kind: .sentence, text: "run", preview: "b"))
        #expect(await history.items().count == 2)
    }

    @Test func onlyTheNewestItemsAreKept() async throws {
        let history = try HistoryStore(fileURL: nil, limit: 3)
        for number in 1...5 {
            await history.record(word("word\(number)", at: TimeInterval(number)))
        }
        #expect(await history.items().map(\.text) == ["word5", "word4", "word3"])
    }

    @Test func removeAndRemoveAll() async throws {
        let history = try HistoryStore(fileURL: nil)
        await history.record(word("apple", at: 1))
        await history.record(word("pear", at: 2))

        await history.remove(id: word("apple").id)
        #expect(await history.items().map(\.text) == ["pear"])

        await history.removeAll()
        #expect(await history.items().isEmpty)
    }

    @Test func theItemSurvivesTheFile() async throws {
        let folder = FileManager.default.temporaryDirectory.appending(path: "history test \(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appending(path: "history.sqlite")

        try await HistoryStore(fileURL: file).record(word("apple", at: 1))
        let reopened = try HistoryStore(fileURL: file)
        #expect(await reopened.items() == [word("apple", at: 1)])
    }

    // MARK: - Items

    @Test func aWordItemTakesTheTextFromTheFormAndTheLabelFromTheFirstSense() {
        let sense = Sense(pos: "verb", cefr: "A2", simpleDefinition: "to move fast", vietnamese: "chạy", examples: [])
        let entry = WordEntry(word: "run", ipa: nil, senses: [sense], matchedForm: MatchedForm(text: "ran", type: "past tense"))
        let item = SavedItem(entry: entry)
        #expect(item.text == "ran")
        #expect(item.chip == "verb · A2")
        #expect(item.preview == "to move fast")
    }

    @Test func aPhraseHasNoLabelAndIsMarkedAsPhrase() {
        let sense = Sense(pos: "phrasal verb", cefr: "B1", simpleDefinition: "to stop trying", vietnamese: "bỏ cuộc", examples: [])
        let item = SavedItem(entry: WordEntry(word: "give up", ipa: nil, senses: [sense]))
        #expect(item.chip == nil)
        #expect(item.isPhrase)
    }

    @Test func theKeyIgnoresCaseForWordsOnly() {
        #expect(SavedItem.key(kind: .word, text: " Run ") == SavedItem.key(kind: .word, text: "run"))
        #expect(SavedItem.key(kind: .sentence, text: "US") != SavedItem.key(kind: .sentence, text: "us"))
    }

    // MARK: - Day groups and search

    @Test func itemsAreGroupedByDay() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "UTC"))
        let now = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 19, hour: 12)))
        let start = now.timeIntervalSince1970
        let items = [
            word("a", at: start),
            word("b", at: start - 3600),
            word("c", at: start - 86400),
            word("d", at: start - 3 * 86400),
        ]

        let groups = DayGroup.groups(of: items, now: now, calendar: calendar, locale: Locale(identifier: "en_US"))

        #expect(groups.map(\.title).prefix(2) == ["Today", "Yesterday"])
        #expect(groups.map { $0.items.map(\.text) } == [["a", "b"], ["c"], ["d"]])
    }

    @Test func searchLooksAtTheTextAndTheMeaning() {
        let items = [word("apple", preview: "a round fruit"), word("pear", preview: "a sweet fruit"), word("car", preview: "a vehicle")]
        #expect(items.matching("APP").map(\.text) == ["apple"])
        #expect(items.matching("fruit").map(\.text) == ["apple", "pear"])
        #expect(items.matching("  ").count == 3)
    }
}
