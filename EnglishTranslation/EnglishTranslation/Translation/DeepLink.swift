//
//  DeepLink.swift
//  EnglishTranslation
//

import Foundation

/// The `saynata://lookup?text=...` link. The share extension uses it to open the app with the same text.
/// The scheme is registered in the app's Info.plist (`CFBundleURLTypes`).
enum DeepLink {
    static let scheme = "saynata"
    private static let host = "lookup"

    /// The link that opens the app on `text`. Nil only if the URL cannot be built.
    static func lookupURL(text: String) -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = host
        components.queryItems = [URLQueryItem(name: "text", value: text)]
        return components.url
    }

    /// The text inside a lookup link. Nil for any other URL, or when the text is empty.
    static func text(from url: URL) -> String? {
        guard url.scheme == scheme, url.host() == host,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let text = components.queryItems?.first(where: { $0.name == "text" })?.value,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return nil }
        return text
    }
}
