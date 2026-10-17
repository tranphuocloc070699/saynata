//
//  AccountStoreTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct AccountStoreTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()

    private func makeStore(saved: String? = nil) -> AccountStore {
        if let saved { try? secrets.setSecret(saved, forKey: "session") }
        return AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
    }

    private var signedInJSON: String { savedSessionJSON(access: "A1", refresh: "R1") }

    // MARK: - Signing in

    @Test func aGuestStartsWithNoUserAndNoToken() async {
        let store = makeStore()
        #expect(store.user == nil)
        #expect(store.isSignedIn == false)
        #expect(await store.accessToken() == nil)
    }

    @Test func signInSavesBothTokensInTheKeychainStore() async throws {
        server.handler = { _ in tokenReply(access: "A1", refresh: "R1") }
        let store = makeStore()

        try await store.signIn(email: " Learner@Example.com ", code: "123456")

        #expect(store.user == AuthUser(id: 7, email: "learner@example.com"))
        #expect(await store.accessToken() == "A1")
        let saved = try #require(secrets.secret(forKey: "session"))
        #expect(saved.contains("A1"))
        #expect(saved.contains("R1"))
        // The request had the email trimmed.
        #expect(server.requests.first?.json["email"] as? String == "Learner@Example.com")
    }

    @Test func aWrongCodeLeavesTheUserSignedOut() async {
        server.handler = { _ in .problem(401, code: "invalid_code") }
        let store = makeStore()

        await #expect(throws: AuthError.invalidCode) {
            try await store.signIn(email: "a@b.co", code: "000000")
        }

        #expect(store.isSignedIn == false)
        #expect(secrets.all.isEmpty)
    }

    @Test func aSavedSessionIsLoadedAtStart() async {
        let store = makeStore(saved: signedInJSON)
        #expect(store.user?.email == "learner@example.com")
        #expect(await store.accessToken() == "A1")
    }

    @Test func aBrokenSavedSessionCountsAsSignedOut() {
        let store = makeStore(saved: "not json")
        #expect(store.isSignedIn == false)
    }

    // MARK: - Signing out

    @Test func signOutClearsTheKeychainAndRevokesTheRefreshToken() async {
        server.handler = { _ in .empty(204) }
        let store = makeStore(saved: signedInJSON)

        await store.signOut()

        #expect(store.isSignedIn == false)
        #expect(await store.accessToken() == nil)
        #expect(secrets.secret(forKey: "session") == nil)
        let request = server.requests(to: "/api/auth/sign-out").first
        #expect(request?.json["refreshToken"] as? String == "R1")
    }

    @Test func signOutWorksWhenTheServerIsDown() async {
        server.handler = { _ in .empty(500) }
        let store = makeStore(saved: signedInJSON)

        await store.signOut()

        #expect(store.isSignedIn == false)
        #expect(secrets.secret(forKey: "session") == nil)
    }

    // MARK: - Refresh

    @Test func refreshSavesTheNewPair() async throws {
        server.handler = { _ in tokenReply(access: "A2", refresh: "R2") }
        let store = makeStore(saved: signedInJSON)

        let fresh = try await store.refreshedAccessToken(replacing: "A1")

        #expect(fresh == "A2")
        #expect(await store.accessToken() == "A2")
        #expect(server.requests.first?.json["refreshToken"] as? String == "R1")
        #expect(secrets.secret(forKey: "session")?.contains("R2") == true)
    }

    @Test func aRefusedRefreshTokenSignsOutLocally() async throws {
        server.handler = { _ in .problem(401, code: "invalid_refresh_token") }
        let store = makeStore(saved: signedInJSON)

        let fresh = try await store.refreshedAccessToken(replacing: "A1")

        #expect(fresh == nil)
        #expect(store.isSignedIn == false)
        #expect(secrets.secret(forKey: "session") == nil)
    }

    @Test func aRefreshThatCannotReachTheServerKeepsTheSession() async {
        try? secrets.setSecret(signedInJSON, forKey: "session")
        // No fake server has this host, so the request fails like a network error.
        let store = AccountStore(
            secrets: secrets, client: AuthClient(baseURL: URL(string: "http://nobody.test"), session: server.session))

        await #expect(throws: URLError.self) {
            _ = try await store.refreshedAccessToken(replacing: "A1")
        }

        #expect(store.isSignedIn)
        #expect(secrets.secret(forKey: "session") != nil)
    }

    @Test func aTokenThatWasAlreadyReplacedIsNotRefreshedAgain() async throws {
        server.handler = { _ in tokenReply(access: "A2", refresh: "R2") }
        let store = makeStore(saved: signedInJSON)

        _ = try await store.refreshedAccessToken(replacing: "A1")
        // A second request that failed with the old token asks later: it gets the new token, with no new refresh.
        let second = try await store.refreshedAccessToken(replacing: "A1")

        #expect(second == "A2")
        #expect(server.requests(to: "/api/auth/refresh").count == 1)
    }

    // MARK: - Shared with the extensions (one Keychain item, two processes)

    @Test func aSessionAnotherProcessSavedIsUsed() async throws {
        let store = makeStore()
        // The app signs in while the extension's store is already alive.
        try secrets.setSecret(signedInJSON, forKey: "session")

        #expect(await store.accessToken() == "A1")
        #expect(store.isSignedIn)
    }

    @Test func aRefreshAnotherProcessDidIsNotDoneAgain() async throws {
        server.handler = { _ in tokenReply(access: "A3", refresh: "R3") }
        let store = makeStore(saved: signedInJSON)
        // The other process refreshed: R1 is used up on the server, the Keychain holds the new pair.
        try secrets.setSecret(savedSessionJSON(access: "A2", refresh: "R2"), forKey: "session")

        let fresh = try await store.refreshedAccessToken(replacing: "A1")

        #expect(fresh == "A2")
        #expect(server.requests(to: "/api/auth/refresh").isEmpty)
    }

    @Test func aRefreshUsesTheNewestRefreshTokenFromTheKeychain() async throws {
        server.handler = { _ in tokenReply(access: "A3", refresh: "R3") }
        let store = makeStore(saved: signedInJSON)
        try secrets.setSecret(savedSessionJSON(access: "A2", refresh: "R2"), forKey: "session")

        // A2 ended too: the refresh must send R2, never the used R1.
        let fresh = try await store.refreshedAccessToken(replacing: "A2")

        #expect(fresh == "A3")
        #expect(server.requests.first?.json["refreshToken"] as? String == "R2")
    }

    @Test func keychainStoreSavesReadsAndDeletes() throws {
        // A test service name, so the real tokens are never touched. In the simulator test build the shared group
        // may be missing: then the store falls back to its own group, which is the free-account path.
        let store = KeychainSecretStore(service: "com.luke.EnglishTranslation.tests.\(UUID().uuidString)")
        defer { try? store.setSecret(nil, forKey: "session") }

        try store.setSecret("one", forKey: "session")
        try store.setSecret("two", forKey: "session")
        #expect(store.secret(forKey: "session") == "two")

        try store.setSecret(nil, forKey: "session")
        #expect(store.secret(forKey: "session") == nil)
    }

    @Test func requestsThatFailTogetherShareOneRefresh() async throws {
        server.handler = { _ in tokenReply(access: "A2", refresh: "R2") }
        let store = makeStore(saved: signedInJSON)

        async let first = store.refreshedAccessToken(replacing: "A1")
        async let second = store.refreshedAccessToken(replacing: "A1")
        let tokens = try await [first, second]

        #expect(tokens == ["A2", "A2"])
        // The server accepts each refresh token once, so a second call would sign the user out.
        #expect(server.requests(to: "/api/auth/refresh").count == 1)
    }

    // MARK: - Delete account

    @Test func deleteAccountCallsTheServerThenSignsOutLocally() async throws {
        server.handler = { _ in .empty(204) }
        let store = makeStore(saved: signedInJSON)

        try await store.deleteAccount()

        let request = try #require(server.requests.first)
        #expect(request.method == "DELETE")
        #expect(request.path == "/api/me")
        #expect(request.authorization == "Bearer A1")
        #expect(store.isSignedIn == false)
        #expect(secrets.secret(forKey: "session") == nil)
    }

    @Test func deleteAccountRefreshesAnEndedTokenOnce() async throws {
        server.handler = { request in
            switch request.path {
            case "/api/me": request.authorization == "Bearer A2" ? .empty(204) : .empty(401)
            default: tokenReply(access: "A2", refresh: "R2")
            }
        }
        let store = makeStore(saved: signedInJSON)

        try await store.deleteAccount()

        #expect(server.requests.map(\.path) == ["/api/me", "/api/auth/refresh", "/api/me"])
        #expect(store.isSignedIn == false)
    }

    @Test func anAdminAccountStaysSignedInWhenDeleteIsRefused() async {
        server.handler = { _ in .problem(403, code: "admin_account", detail: "An admin account cannot be deleted here.") }
        let store = makeStore(saved: signedInJSON)

        await #expect(throws: AuthError.adminAccount("An admin account cannot be deleted here.")) {
            try await store.deleteAccount()
        }

        #expect(store.isSignedIn)
        #expect(secrets.secret(forKey: "session") != nil)
    }

    @Test func deleteAccountDoesNothingForAGuest() async throws {
        let store = makeStore()
        try await store.deleteAccount()
        #expect(server.requests.isEmpty)
    }
}
