//
//  AccountStore.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// Gives `BackendClient` the access token to send, and a new one when the server says the old one is too old.
/// `AccountStore` is the real one; tests use a fake.
protocol AccessTokenProviding: Sendable {
    /// The access token to send as `Authorization: Bearer`. Nil for a guest.
    func accessToken() async -> String?

    /// Called after a 401 for a request that carried `rejected`. Asks the server for a new token pair and returns
    /// the new access token. Returns nil when the session is over (the user is signed out locally).
    /// Throws when the server cannot be reached: the session stays, so the user can try again.
    func refreshedAccessToken(replacing rejected: String) async throws -> String?
}

/// The signed-in account: who it is, and the tokens (kept in the Keychain). Created once in `EnglishTranslationApp`
/// and shared with `.environment`. A guest has no user and no tokens, and the app works as it did before.
@Observable
final class AccountStore: AccessTokenProviding {
    /// The signed-in person, or nil for a guest. Views watch it.
    private(set) var user: AuthUser?

    var isSignedIn: Bool { user != nil }

    /// Why the last emailed link did not sign in, for the app's alert. Nil when all is fine.
    private(set) var linkSignInError: String?

    /// The one store of this process. The app and the extensions each have one, and they share the session
    /// through the Keychain (`KeychainSecretStore`, App Group), so the extensions are signed in too.
    static let shared = AccountStore()

    @ObservationIgnored private var session: SavedSession?
    @ObservationIgnored private var refreshTask: Task<String?, Error>?
    @ObservationIgnored private let secrets: any SecretStoring
    @ObservationIgnored private let client: AuthClient

    /// The Keychain key. All of the session is one JSON item, so it is saved and deleted in one step.
    private static let sessionKey = "session"
    /// The secret kept while an emailed link is on its way. It survives the app being closed while the user is in Mail.
    private static let linkVerifierKey = "pendingLinkVerifier"

    init(
        secrets: any SecretStoring = KeychainSecretStore(),
        client: AuthClient = AuthClient(baseURL: try? AppConfig.backendBaseURL())
    ) {
        self.secrets = secrets
        self.client = client
        if let saved = Self.savedSession(in: secrets) {
            session = saved
            user = saved.user
        }
    }

    private static func savedSession(in secrets: any SecretStoring) -> SavedSession? {
        guard let json = secrets.secret(forKey: sessionKey) else { return nil }
        return try? JSONDecoder().decode(SavedSession.self, from: Data(json.utf8))
    }

    /// Takes the session another process saved since. The app and an extension share the Keychain item, and the
    /// server accepts each refresh token once: refreshing with an old one would end every session of the user.
    /// An empty Keychain changes nothing here (a refused save keeps the user signed in until the app closes).
    private func reloadSavedSession() {
        guard let saved = Self.savedSession(in: secrets), saved != session else { return }
        session = saved
        if user != saved.user { user = saved.user }
    }

    // MARK: - Signing in

    /// Step 1 of the sheet: the backend emails a 6-digit code. Throws `AuthError` or a `URLError`.
    func requestCode(email: String) async throws {
        try await client.requestCode(email: email.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    /// Step 2 of the sheet: a right code signs the user in and saves the tokens in the Keychain.
    /// Throws `AuthError.invalidCode` (and others) when the server says no.
    func signIn(email: String, code: String) async throws {
        let response = try await client.verify(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            code: code.trimmingCharacters(in: .whitespacesAndNewlines))
        save(SavedSession(accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user))
    }

    /// "Email me a link": makes a new secret, keeps it in the Keychain, and asks the backend to email a link that
    /// only works together with that secret. A new request replaces the old secret (and the server the old link).
    func requestLink(email: String) async throws {
        let verifier = SignInSecrets.random()
        try? secrets.setSecret(verifier, forKey: Self.linkVerifierKey)
        try await client.requestLink(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            codeChallenge: SignInSecrets.sha256Hex(verifier))
    }

    /// The link from the email opened the app. Signs in with the token and the kept secret.
    /// Throws `AuthError.invalidLink` when the link is used, old, or was asked for on another device.
    func signIn(linkToken: String) async throws {
        guard let verifier = secrets.secret(forKey: Self.linkVerifierKey) else { throw AuthError.invalidLink }
        let response = try await client.verifyLink(token: linkToken, codeVerifier: verifier)
        try? secrets.setSecret(nil, forKey: Self.linkVerifierKey)
        save(SavedSession(accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user))
    }

    /// The app was opened by a sign-in link. Signs in, or keeps the reason in `linkSignInError` for the alert.
    func completeLinkSignIn(token: String) async {
        linkSignInError = nil
        do {
            try await signIn(linkToken: token)
        } catch {
            linkSignInError = error.localizedDescription
        }
    }

    func clearLinkSignInError() {
        linkSignInError = nil
    }

    /// Google or Apple gave an ID token for the hashed `nonce`; the backend checks it and answers with our tokens.
    func signIn(with provider: SocialProvider, idToken: String, nonce: String) async throws {
        let response = try await client.signIn(with: provider, idToken: idToken, nonce: nonce)
        save(SavedSession(accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user))
    }

    // MARK: - Signing out

    /// Signs out at once on this device, then tells the server to revoke the refresh token.
    /// A failed server call is ignored: the device is already signed out.
    func signOut() async {
        let refreshToken = session?.refreshToken
        signOutLocally()
        if let refreshToken {
            try? await client.signOut(refreshToken: refreshToken)
        }
    }

    /// `DELETE /api/me`, then sign out on this device. If the access token is too old, it is refreshed once.
    /// Throws `AuthError` (for example `.adminAccount`) or a `URLError`; the user stays signed in then.
    func deleteAccount() async throws {
        guard let token = session?.accessToken else { return }
        do {
            try await client.deleteAccount(accessToken: token)
        } catch AuthError.badStatus(401, _) {
            // The token ended. `/api/me` answers 401 with no body.
            guard let fresh = try await refreshedAccessToken(replacing: token) else { return }
            try await client.deleteAccount(accessToken: fresh)
        }
        signOutLocally()
    }

    // MARK: - AccessTokenProviding

    func accessToken() async -> String? {
        reloadSavedSession()
        return session?.accessToken
    }

    func refreshedAccessToken(replacing rejected: String) async throws -> String? {
        // A refresh already running here has the newest token; else another process may have refreshed.
        if refreshTask == nil { reloadSavedSession() }
        guard let current = session else { return nil }
        // Another request already got a new token while this one waited.
        if current.accessToken != rejected { return current.accessToken }
        // Several requests fail together when a token ends. They all wait for one refresh,
        // because the server accepts each refresh token only once.
        if let refreshTask { return try await refreshTask.value }

        let task = Task { try await refresh(using: current.refreshToken) }
        refreshTask = task
        defer { refreshTask = nil }
        return try await task.value
    }

    private func refresh(using refreshToken: String) async throws -> String? {
        do {
            let response = try await client.refresh(refreshToken: refreshToken)
            save(SavedSession(accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user))
            return response.accessToken
        } catch AuthError.invalidRefreshToken {
            // The server refused the refresh token (ended, used, or revoked): the session is over.
            signOutLocally()
            return nil
        }
    }

    // MARK: - Saving

    private func save(_ saved: SavedSession) {
        session = saved
        user = saved.user
        // If the Keychain refuses, the user stays signed in until the app closes, then signs in again.
        if let json = try? JSONEncoder().encode(saved), let text = String(data: json, encoding: .utf8) {
            try? secrets.setSecret(text, forKey: Self.sessionKey)
        }
    }

    private func signOutLocally() {
        session = nil
        user = nil
        try? secrets.setSecret(nil, forKey: Self.sessionKey)
    }
}
