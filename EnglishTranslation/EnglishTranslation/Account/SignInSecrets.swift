//
//  SignInSecrets.swift
//  EnglishTranslation
//

import CryptoKit
import Foundation

/// Random one-time values for sign-in (TASK-92): the nonce for Google and Apple, and the secret that ties an emailed
/// link to this app (the PKCE idea, RFC 7636: the server keeps only a hash, the app proves it has the secret).
enum SignInSecrets {
    /// 32 random bytes as URL-safe base64 (43 characters).
    static func random() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return base64URL(Data(bytes))
    }

    /// SHA-256 as 64 lower-case hex characters. The backend compares this form.
    static func sha256Hex(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// SHA-256 as URL-safe base64, the PKCE `S256` form Google asks for.
    static func sha256Base64URL(_ text: String) -> String {
        base64URL(Data(SHA256.hash(data: Data(text.utf8))))
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
