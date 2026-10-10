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
    case thai = "th"
    case french = "fr"
    case spanish = "es"
    case german = "de"

    /// Lets `ForEach` loop over the cases.
    var id: Self { self }

    /// The code the backend expects, for example "vi".
    var code: String { rawValue }

    /// The name shown in the picker and in the prompt for the direct providers.
    var title: String {
        switch self {
        case .vietnamese: "Vietnamese"
        case .english: "English"
        case .japanese: "Japanese"
        case .korean: "Korean"
        case .chinese: "Simplified Chinese"
        case .thai: "Thai"
        case .french: "French"
        case .spanish: "Spanish"
        case .german: "German"
        }
    }
}
