//
//  TargetLanguagePreference.swift
//  EnglishTranslation
//

import Foundation

/// Saves the language the learner picked for the translation line (TASK-109) and reads it back at launch.
/// The value is the language code, for example "ko". A missing, broken or removed code (Thai "th") reads as
/// Vietnamese, through `TargetLanguage(savedCode:)`.
///
/// `shared` uses the App Group's defaults, so the app, the Share and Translation extensions, the Quick explain
/// intents and the Mac app all read the same value. Tests pass their own `UserDefaults` suite.
struct TargetLanguagePreference {
    /// The key in `UserDefaults`.
    static let key = "targetLanguage"

    var defaults: UserDefaults

    /// The App Group defaults. If the group is not set up (a free Apple ID build), the normal defaults:
    /// the extensions then keep their own copy, and everything still works.
    /// In a test run each call gives a new empty suite, so a test that picks Korean does not change the next test.
    static var shared: TargetLanguagePreference {
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
            return TargetLanguagePreference(defaults: makeEmptyDefaults())
        }
        return TargetLanguagePreference(defaults: UserDefaults(suiteName: ResultCache.appGroupID) ?? .standard)
    }

    /// The saved language. Vietnamese if nothing usable is saved.
    var language: TargetLanguage {
        get { TargetLanguage(savedCode: defaults.string(forKey: Self.key)) }
        nonmutating set { defaults.set(newValue.code, forKey: Self.key) }
    }

    /// A defaults object with nothing saved in it, for tests. It has its own name, so it never meets real data.
    static func makeEmptyDefaults() -> UserDefaults {
        let name = "TargetLanguagePreference-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name) ?? .standard
        defaults.removePersistentDomain(forName: name)
        return defaults
    }
}
