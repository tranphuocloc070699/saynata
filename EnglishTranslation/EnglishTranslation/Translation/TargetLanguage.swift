//
//  TargetLanguage.swift
//  EnglishTranslation
//

import Foundation

/// The language of the translation at the bottom of the result.
/// The codes must match `TargetLanguage.java` in the backend.
enum TargetLanguage: String, CaseIterable, Identifiable {
    case vietnamese = "vi"
    case english = "en"
    case japanese = "ja"
    case korean = "ko"
    case chinese = "zh"
    case french = "fr"
    case spanish = "es"
    case german = "de"

    /// Reads a saved code (for example from UserDefaults). A code that is gone (Thai "th") or broken
    /// reads as Vietnamese.
    init(savedCode: String?) {
        self = savedCode.flatMap(TargetLanguage.init(rawValue:)) ?? .vietnamese
    }

    /// Lets `ForEach` loop over the cases.
    var id: Self { self }

    /// The code the backend expects, for example "vi".
    var code: String { rawValue }

    /// The name shown in the picker.
    var title: String {
        switch self {
        case .vietnamese: "Vietnamese"
        case .english: "English"
        case .japanese: "Japanese"
        case .korean: "Korean"
        case .chinese: "Simplified Chinese"
        case .french: "French"
        case .spanish: "Spanish"
        case .german: "German"
        }
    }
}
