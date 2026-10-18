//
//  AuthClient.swift
//  EnglishTranslation
//

import Foundation

/// The error body Spring Boot sends: `detail` is the message, `code` is what the app switches on (doc-17).
struct ProblemBody: Decodable {
    let detail: String?
    let code: String?
}

private struct EmailCodeBody: Encodable {
    let email: String
}

private struct VerifyBody: Encodable {
    let email: String
    let code: String
}

private struct RefreshBody: Encodable {
    let refreshToken: String
}

private struct EmailLinkBody: Encodable {
    let email: String
    let codeChallenge: String
}

private struct EmailLinkVerifyBody: Encodable {
    let token: String
    let codeVerifier: String
}

private struct IdTokenBody: Encodable {
    let idToken: String
    let nonce: String
}

/// The outside accounts the backend accepts (`POST /api/auth/google`, `POST /api/auth/apple`).
enum SocialProvider: String, Sendable {
    case google
    case apple
}

/// The plain calls to `/api/auth/*` and `/api/me`. It keeps no state: `AccountStore` decides what to save.
struct AuthClient: Sendable {
    /// The API root. Nil when `BackendBaseURL` is missing: every call then throws `AppConfig.ConfigError`.
    let baseURL: URL?
    /// Tests pass a session with a fake `URLProtocol`.
    var session: URLSession = .shared

    /// `POST /api/auth/email-code`. The backend sends a 6-digit code to the email. Answers 204 for any email.
    func requestCode(email: String) async throws {
        _ = try await send("api/auth/email-code", method: "POST", body: EmailCodeBody(email: email))
    }

    /// `POST /api/auth/verify`. A right code gives the tokens and the user.
    func verify(email: String, code: String) async throws -> AuthTokenResponse {
        let data = try await send("api/auth/verify", method: "POST", body: VerifyBody(email: email, code: code))
        return try JSONDecoder().decode(AuthTokenResponse.self, from: data)
    }

    /// `POST /api/auth/email-link`. The backend emails a sign-in link. `codeChallenge` is the SHA-256 (hex) of the
    /// secret this app keeps; only the app that has the secret can use the link. Answers 204 for any email.
    func requestLink(email: String, codeChallenge: String) async throws {
        _ = try await send("api/auth/email-link", method: "POST",
                           body: EmailLinkBody(email: email, codeChallenge: codeChallenge))
    }

    /// `POST /api/auth/email-link/verify`. The token from the link and the kept secret give the tokens and the user.
    func verifyLink(token: String, codeVerifier: String) async throws -> AuthTokenResponse {
        let data = try await send("api/auth/email-link/verify", method: "POST",
                                  body: EmailLinkVerifyBody(token: token, codeVerifier: codeVerifier))
        return try JSONDecoder().decode(AuthTokenResponse.self, from: data)
    }

    /// `POST /api/auth/google` or `/apple`. `nonce` is the raw nonce; the ID token holds its SHA-256.
    func signIn(with provider: SocialProvider, idToken: String, nonce: String) async throws -> AuthTokenResponse {
        let data = try await send("api/auth/\(provider.rawValue)", method: "POST",
                                  body: IdTokenBody(idToken: idToken, nonce: nonce))
        return try JSONDecoder().decode(AuthTokenResponse.self, from: data)
    }

    /// `POST /api/auth/refresh`. The old refresh token stops working; the answer has a new pair.
    /// Throws `AuthError.invalidRefreshToken` when the server no longer accepts the token.
    func refresh(refreshToken: String) async throws -> AuthTokenResponse {
        let data = try await send("api/auth/refresh", method: "POST", body: RefreshBody(refreshToken: refreshToken))
        return try JSONDecoder().decode(AuthTokenResponse.self, from: data)
    }

    /// `POST /api/auth/sign-out`. The server revokes the refresh token.
    func signOut(refreshToken: String) async throws {
        _ = try await send("api/auth/sign-out", method: "POST", body: RefreshBody(refreshToken: refreshToken))
    }

    /// `DELETE /api/me`. Removes the account on the server.
    func deleteAccount(accessToken: String) async throws {
        _ = try await send("api/me", method: "DELETE", bearer: accessToken)
    }

    // MARK: - Sending

    private func send(
        _ path: String,
        method: String,
        body: (any Encodable)? = nil,
        bearer: String? = nil
    ) async throws -> Data {
        guard let baseURL else { throw AppConfig.ConfigError.missing("BackendBaseURL") }
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }
        if let bearer {
            request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        let status = http?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let problem = try? JSONDecoder().decode(ProblemBody.self, from: data)
            let retryAfter = http?.value(forHTTPHeaderField: "Retry-After").flatMap { Int($0) }
            throw Self.error(status: status, problem: problem, retryAfter: retryAfter, path: path)
        }
        return data
    }

    /// Turns a failed reply into an `AuthError`. The code is read first, then the status.
    static func error(status: Int, problem: ProblemBody?, retryAfter: Int?, path: String) -> AuthError {
        if status == 429 || problem?.code == "rate_limited" { return .rateLimited(retryAfter: retryAfter) }
        switch (status, problem?.code) {
        case (400, "invalid_email"): return .invalidEmail(problem?.detail)
        case (401, "invalid_code"): return .invalidCode
        case (401, "invalid_refresh_token"): return .invalidRefreshToken
        case (401, "invalid_link"): return .invalidLink
        case (401, "invalid_id_token"): return .invalidIdToken(problem?.detail)
        case (503, "provider_not_configured"): return .providerNotConfigured
        case (403, "admin_account"): return .adminAccount(problem?.detail)
        default: break
        }
        // A 401 without a code: the path tells what was refused.
        if status == 401, path.hasSuffix("email-link/verify") { return .invalidLink }
        if status == 401, path.hasSuffix("verify") { return .invalidCode }
        if status == 401, path.hasSuffix("refresh") { return .invalidRefreshToken }
        return .badStatus(status, detail: problem?.detail)
    }
}
