//
//  Provider.swift
//  EnglishTranslation
//

import Foundation

/// The service that explains a sentence. `server` is our Java backend; the others call an LLM
/// directly from the phone with a key from `Secrets.xcconfig`.
enum Provider: String, CaseIterable, Identifiable {
    case server
    case deepSeek
    case openAI
    case claude

    /// Lets `ForEach` loop over the cases.
    var id: Self { self }

    /// The name shown in the picker.
    var title: String {
        switch self {
        case .server: "Server"
        case .deepSeek: "DeepSeek"
        case .openAI: "ChatGPT"
        case .claude: "Claude"
        }
    }
}
