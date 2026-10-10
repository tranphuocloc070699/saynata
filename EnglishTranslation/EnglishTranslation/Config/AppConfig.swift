//
//  AppConfig.swift
//  EnglishTranslation
//
//  Created by Trần Phước Lộc on 7/10/26.
//

import Foundation

/// Values that come from `Secrets.xcconfig` through the Info.plist.
enum AppConfig {
    /// The error shown when a required value is missing.
    enum ConfigError: LocalizedError {
        case missing(String)

        var errorDescription: String? {
            switch self {
            case let .missing(name):
                "Missing \(name). Check Secrets.xcconfig and the Info tab."
            }
        }
    }

    /// The Java backend root, from `BackendBaseURL` in Info.plist. Throws if it is missing or not a URL.
    static func backendBaseURL() throws -> URL {
        guard let url = URL(string: try value(for: "BackendBaseURL")) else {
            throw ConfigError.missing("BackendBaseURL")
        }
        return url
    }

    /// The API key for `provider`. Throws if the key is missing or empty.
    static func apiKey(for provider: Provider) throws -> String {
        switch provider {
        case .server: preconditionFailure("The server provider has no API key.")
        case .deepSeek: try value(for: "DeepSeekAPIKey")
        case .openAI: try value(for: "OpenAIAPIKey")
        case .claude: try value(for: "ClaudeAPIKey")
        }
    }

    private static func value(for name: String) throws -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: name) as? String,
              !value.isEmpty
        else {
            throw ConfigError.missing(name)
        }
        return value
    }
}
