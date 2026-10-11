//
//  AppConfig.swift
//  EnglishTranslation
//
//  Created by Trần Phước Lộc on 7/10/26.
//

import Foundation

/// Values that come from the Info.plist.
enum AppConfig {
    /// The error shown when a required value is missing.
    enum ConfigError: LocalizedError {
        case missing(String)

        var errorDescription: String? {
            switch self {
            case let .missing(name):
                "Missing \(name). Check the Info tab."
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

    private static func value(for name: String) throws -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: name) as? String,
              !value.isEmpty
        else {
            throw ConfigError.missing(name)
        }
        return value
    }
}
