//
//  AuthClientTests.swift
//  EnglishTranslationTests
//

import Foundation
import Security
import Testing
@testable import EnglishTranslation

@MainActor
struct AuthClientTests {
    private let server = ScriptedServer()
    private var client: AuthClient { AuthClient(baseURL: server.baseURL, session: server.session) }

    // MARK: - Requests

    @Test func requestCodePostsTheEmail() async throws {
        server.handler = { _ in .empty(204) }

        try await client.requestCode(email: "learner@example.com")

        let request = try #require(server.requests.first)
        #expect(request.method == "POST")
        #expect(request.path == "/api/auth/email-code")
        #expect(request.json["email"] as? String == "learner@example.com")
        #expect(request.authorization == nil)
    }

    @Test func verifyPostsEmailAndCodeAndDecodesTheTokens() async throws {
        server.handler = { _ in tokenReply(access: "A1", refresh: "R1") }

        let response = try await client.verify(email: "learner@example.com", code: "123456")

        let request = try #require(server.requests.first)
        #expect(request.path == "/api/auth/verify")
        #expect(request.json["email"] as? String == "learner@example.com")
        #expect(request.json["code"] as? String == "123456")
        #expect(response == AuthTokenResponse(
            accessToken: "A1", refreshToken: "R1", expiresIn: 3600,
            user: AuthUser(id: 7, email: "learner@example.com")))
    }

    @Test func refreshPostsTheRefreshToken() async throws {
        server.handler = { _ in tokenReply(access: "A2", refresh: "R2") }

        let response = try await client.refresh(refreshToken: "R1")

        let request = try #require(server.requests.first)
        #expect(request.path == "/api/auth/refresh")
        #expect(request.json["refreshToken"] as? String == "R1")
        #expect(response.accessToken == "A2")
    }

    @Test func signOutPostsTheRefreshToken() async throws {
        server.handler = { _ in .empty(204) }

        try await client.signOut(refreshToken: "R1")

        let request = try #require(server.requests.first)
        #expect(request.path == "/api/auth/sign-out")
        #expect(request.json["refreshToken"] as? String == "R1")
    }

    @Test func deleteAccountSendsDeleteWithTheBearerToken() async throws {
        server.handler = { _ in .empty(204) }

        try await client.deleteAccount(accessToken: "A1")

        let request = try #require(server.requests.first)
        #expect(request.method == "DELETE")
        #expect(request.path == "/api/me")
        #expect(request.authorization == "Bearer A1")
    }

    @Test func aMissingBaseURLThrowsAConfigError() async {
        let client = AuthClient(baseURL: nil, session: server.session)
        await #expect(throws: AppConfig.ConfigError.self) {
            try await client.requestCode(email: "a@b.co")
        }
    }

    // MARK: - Errors

    @Test func aWrongCodeIsInvalidCode() async {
        server.handler = { _ in .problem(401, code: "invalid_code") }
        await #expect(throws: AuthError.invalidCode) {
            _ = try await client.verify(email: "a@b.co", code: "000000")
        }
    }

    @Test func a401OnVerifyWithoutACodeIsStillInvalidCode() async {
        server.handler = { _ in .empty(401) }
        await #expect(throws: AuthError.invalidCode) {
            _ = try await client.verify(email: "a@b.co", code: "000000")
        }
    }

    @Test func aBadRefreshTokenIsInvalidRefreshToken() async {
        server.handler = { _ in .problem(401, code: "invalid_refresh_token") }
        await #expect(throws: AuthError.invalidRefreshToken) {
            _ = try await client.refresh(refreshToken: "old")
        }
    }

    @Test func aBadEmailShowsTheServerMessage() async {
        server.handler = { _ in .problem(400, code: "invalid_email", detail: "Enter a valid email address.") }
        await #expect(throws: AuthError.invalidEmail("Enter a valid email address.")) {
            try await client.requestCode(email: "nope")
        }
    }

    @Test func rateLimitedReadsTheRetryAfterHeader() async {
        server.handler = { _ in .problem(429, code: "rate_limited", headers: ["Retry-After": "900"]) }
        await #expect(throws: AuthError.rateLimited(retryAfter: 900)) {
            try await client.requestCode(email: "a@b.co")
        }
    }

    @Test func anAdminAccountCannotBeDeleted() async {
        server.handler = { _ in .problem(403, code: "admin_account", detail: "An admin account cannot be deleted here.") }
        await #expect(throws: AuthError.adminAccount("An admin account cannot be deleted here.")) {
            try await client.deleteAccount(accessToken: "A1")
        }
    }

    @Test func anUnknownStatusKeepsStatusAndDetail() async {
        server.handler = { _ in .problem(500, code: nil, detail: "Boom") }
        await #expect(throws: AuthError.badStatus(500, detail: "Boom")) {
            try await client.requestCode(email: "a@b.co")
        }
    }

    // MARK: - Messages

    @Test func rateLimitMessagesRoundUpToWholeMinutes() {
        #expect(RateLimitMessage.text(retryAfter: 900) == "Too many requests. Try again in 15 minutes.")
        #expect(RateLimitMessage.text(retryAfter: 61) == "Too many requests. Try again in 2 minutes.")
        #expect(RateLimitMessage.text(retryAfter: 30) == "Too many requests. Try again in 1 minute.")
        #expect(RateLimitMessage.text(retryAfter: nil) == "Too many requests. Try again in a little while.")
    }
}

// MARK: - Keychain

/// Talks to the real simulator Keychain, with its own service name so it never touches the app's tokens.
struct KeychainSecretStoreTests {
    private let store = KeychainSecretStore(service: "com.luke.EnglishTranslation.tests.\(UUID().uuidString)")

    @Test func savesReadsReplacesAndDeletes() throws {
        defer { try? store.setSecret(nil, forKey: "session") }

        #expect(store.secret(forKey: "session") == nil)

        try store.setSecret("first", forKey: "session")
        #expect(store.secret(forKey: "session") == "first")

        try store.setSecret("second", forKey: "session")
        #expect(store.secret(forKey: "session") == "second")

        try store.setSecret(nil, forKey: "session")
        #expect(store.secret(forKey: "session") == nil)
    }

    @Test func deletingSomethingThatIsNotThereIsFine() throws {
        try store.setSecret(nil, forKey: "never-saved")
    }

    @Test func keysAreSeparate() throws {
        defer {
            try? store.setSecret(nil, forKey: "a")
            try? store.setSecret(nil, forKey: "b")
        }
        try store.setSecret("one", forKey: "a")
        try store.setSecret("two", forKey: "b")
        #expect(store.secret(forKey: "a") == "one")
        #expect(store.secret(forKey: "b") == "two")
    }
}
