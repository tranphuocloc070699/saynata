//
//  TranslationRowPreference.swift
//  EnglishTranslation
//

import Foundation

/// How the target-language row (for example "Vietnamese") opens on word cards and sentence results.
enum TranslationRowMode: String, CaseIterable, Identifiable {
    /// Closed every time. This is the default (CLAUDE.md "Product rule").
    case collapsed
    /// Open every time.
    case alwaysOpen
    /// Starts the way the learner last left a row.
    case rememberLast

    var id: Self { self }

    /// The text in the Settings list.
    var title: String {
        switch self {
        case .collapsed: "Closed"
        case .alwaysOpen: "Always open"
        case .rememberLast: "Remember"
        }
    }

    /// The short line under the title in the Settings list.
    var hint: String {
        switch self {
        case .collapsed: "Tap to open each time"
        case .alwaysOpen: "Shown under every result"
        case .rememberLast: "Stays how you left it"
        }
    }
}

/// Reads and saves the "Show translation" setting. Views ask `startsOpen` when a row appears,
/// and call `rowChanged(isOpen:)` when the learner opens or closes a row.
/// The values live in `UserDefaults`, so they stay after the app closes.
struct TranslationRowPreference {
    /// Same key as `@AppStorage` in the Settings screen.
    static let modeKey = "showTranslationMode"
    private static let lastOpenKey = "showTranslationLastOpen"

    var defaults: UserDefaults = .standard

    /// The saved mode. Collapsed if nothing is saved yet.
    var mode: TranslationRowMode {
        get { TranslationRowMode(rawValue: defaults.string(forKey: Self.modeKey) ?? "") ?? .collapsed }
        nonmutating set { defaults.set(newValue.rawValue, forKey: Self.modeKey) }
    }

    /// The last open or closed state the learner set on any target-language row.
    private var lastOpen: Bool {
        get { defaults.bool(forKey: Self.lastOpenKey) }
        nonmutating set { defaults.set(newValue, forKey: Self.lastOpenKey) }
    }

    /// Should a new result show its target-language row open?
    var startsOpen: Bool {
        switch mode {
        case .collapsed: false
        case .alwaysOpen: true
        case .rememberLast: lastOpen
        }
    }

    /// Call when the learner taps a target-language row. Only "Remember last choice" uses it.
    func rowChanged(isOpen: Bool) {
        lastOpen = isOpen
    }
}
