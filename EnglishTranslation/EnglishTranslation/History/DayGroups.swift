//
//  DayGroups.swift
//  EnglishTranslation
//

import Foundation

/// A day of items in the History or Liked list, with its heading: "Today", "Yesterday", or the date.
struct DayGroup: Equatable, Identifiable {
    let title: String
    let items: [SavedItem]
    var id: String { title }

    /// Groups items that are already newest first. The order inside a group and between groups is kept.
    static func groups(
        of items: [SavedItem],
        now: Date = .now,
        calendar: Calendar = .current,
        locale: Locale = .current
    ) -> [DayGroup] {
        var result: [DayGroup] = []
        var lastDay: Date?
        for item in items {
            let day = calendar.startOfDay(for: item.date)
            if day == lastDay, let last = result.popLast() {
                result.append(DayGroup(title: last.title, items: last.items + [item]))
            } else {
                result.append(DayGroup(title: title(for: day, now: now, calendar: calendar, locale: locale), items: [item]))
                lastDay = day
            }
        }
        return result
    }

    private static func title(for day: Date, now: Date, calendar: Calendar, locale: Locale) -> String {
        if calendar.isDate(day, inSameDayAs: now) { return "Today" }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(day, inSameDayAs: yesterday) {
            return "Yesterday"
        }
        return day.formatted(Date.FormatStyle(date: .abbreviated, time: .omitted).locale(locale))
    }
}

extension [SavedItem] {
    /// The items that match what the user typed in the search box, in the word or in its short meaning.
    /// Empty search: all items.
    func matching(_ search: String) -> [SavedItem] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return self }
        return filter { item in
            item.text.localizedStandardContains(query) || item.preview.localizedStandardContains(query)
        }
    }
}
