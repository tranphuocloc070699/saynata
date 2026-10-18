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

    /// The token of an emailed sign-in link (TASK-92, doc-17), or nil for any other URL.
    /// Today the link page opens `saynata://sign-in?token=...`. With the paid account and Associated Domains, iOS
    /// opens the email's own `https://<backend>/auth/link?token=...` in the app directly (a universal link); iOS only
    /// does that for our associated domain, so the host needs no check here.
    static func signInToken(from url: URL) -> String? {
        let isSchemeLink = url.scheme == scheme && url.host() == "sign-in"
        let isUniversalLink = url.scheme == "https" && url.path() == "/auth/link"
        guard isSchemeLink || isUniversalLink,
              let token = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                  .queryItems?.first(where: { $0.name == "token" })?.value,
              token.range(of: "^[A-Za-z0-9_-]{20,100}$", options: .regularExpression) != nil
        else { return nil }
        return token
    }
}
