//
//  AuthModels.swift
//  EnglishTranslation
//

import Foundation

/// The signed-in person, as the backend sends it: `{ id, email }`.
struct AuthUser: Codable, Equatable {
    let id: Int
    let email: String
}

/// The answer of `/api/auth/verify` and `/api/auth/refresh`.
struct AuthTokenResponse: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    /// Seconds until the access token ends. We do not read it: a 401 tells us when the token is too old.
    let expiresIn: Int
    let user: AuthUser
}

/// What we keep between launches (as one JSON item in the Keychain).
struct SavedSession: Codable, Equatable {
    var accessToken: String
    var refreshToken: String
    var user: AuthUser
}

/// Why a sign-in call failed. `errorDescription` is the text the sheet shows.
enum AuthError: LocalizedError, Equatable {
    /// `invalid_email` (400). The server's own message.
    case invalidEmail(String?)
    /// `invalid_code` (401): wrong, expired, used or locked. The server gives one answer for all of them.
    case invalidCode
    /// `invalid_refresh_token` (401): the refresh token is no longer good.
    case invalidRefreshToken
    /// `invalid_link` (401): the emailed link is used, old, or was asked for on another device.
    case invalidLink
    /// `invalid_id_token` (401): Google or Apple sign-in was refused. The server's own message.
    case invalidIdToken(String?)
    /// 503 `provider_not_configured`: the backend has no client ID for this provider yet.
    case providerNotConfigured
    /// 429 `rate_limited`, with the seconds from `Retry-After` when there is one.
    case rateLimited(retryAfter: Int?)
    /// 403 `admin_account`: `DELETE /api/me` refuses an admin account.
    case adminAccount(String?)
    /// Any other status.
    case badStatus(Int, detail: String?)

    var errorDescription: String? {
        switch self {
        case let .invalidEmail(message): message ?? "Enter a valid email address."
        case .invalidCode: "That code is wrong or has expired."
        case .invalidRefreshToken: "Your session has ended. Sign in again."
        case .invalidLink: "This link is used or has expired. Ask for a new one on this iPhone."
        case let .invalidIdToken(message): message ?? "Sign-in did not work. Try again."
        case .providerNotConfigured: "This sign-in option is not set up yet. Use your email."
        case let .rateLimited(retryAfter): RateLimitMessage.text(retryAfter: retryAfter)
        case let .adminAccount(message): message ?? "An admin account cannot be deleted here."
        case let .badStatus(code, detail?): "\(detail) (status \(code))"
        case let .badStatus(code, detail: nil): "The server answered with status \(code)."
        }
    }
}

/// The "Too many requests" text, shared by the result area and the sheet.
enum RateLimitMessage {
    /// "Too many requests. Try again in 3 minutes." `retryAfter` is in seconds and rounds up to whole minutes.
    /// Without it (the stream sends no header), the text says "in a little while".
    static func text(retryAfter seconds: Int?) -> String {
        guard let seconds, seconds > 0 else { return "Too many requests. Try again in a little while." }
        let minutes = max(1, (seconds + 59) / 60)
        return "Too many requests. Try again in \(minutes) \(minutes == 1 ? "minute" : "minutes")."
    }
}
