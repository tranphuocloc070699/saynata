//
//  OtherSignInTests.swift
//  EnglishTranslationTests
//
//  TASK-92: the emailed sign-in link, Sign in with Google and Sign in with Apple, against a fake backend.
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct OtherSignInTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()

    private func makeAccount() -> AccountStore {
        AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
    }

    // MARK: - Link

    @Test func askingForALinkKeepsASecretAndSendsOnlyItsHash() async throws {
        server.handler = { _ in .empty(204) }
        let account = makeAccount()

        try await account.requestLink(email: " learner@example.com ")

        let verifier = try #require(secrets.all["pendingLinkVerifier"])
        let body = try #require(server.requests(to: "/api/auth/email-link").first).json
        #expect(body["email"] as? String == "learner@example.com")
        #expect(body["codeChallenge"] as? String == SignInSecrets.sha256Hex(verifier))
        #expect((body["codeChallenge"] as? String)?.count == 64)
    }

    @Test func theLinkSignsInWithTheKeptSecretAndForgetsIt() async throws {
        server.handler = { request in
            switch request.path {
            case "/api/auth/email-link": .empty(204)
            case "/api/auth/email-link/verify": tokenReply(access: "A1", refresh: "R1")
            default: .empty(404)
            }
        }
        let account = makeAccount()
        try await account.requestLink(email: "learner@example.com")
        let verifier = try #require(secrets.all["pendingLinkVerifier"])

        await account.completeLinkSignIn(token: "the-token-from-the-email-1234567890")

        #expect(account.isSignedIn)
        #expect(account.linkSignInError == nil)
        let body = try #require(server.requests(to: "/api/auth/email-link/verify").first).json
        #expect(body["token"] as? String == "the-token-from-the-email-1234567890")
        #expect(body["codeVerifier"] as? String == verifier)
        #expect(secrets.all["pendingLinkVerifier"] == nil)
    }

    @Test func aUsedLinkShowsAnErrorAndKeepsTheUserOut() async throws {
        server.handler = { request in
            request.path == "/api/auth/email-link" ? .empty(204) : .problem(401, code: "invalid_link")
        }
        let account = makeAccount()
        try await account.requestLink(email: "learner@example.com")

        await account.completeLinkSignIn(token: "the-token-from-the-email-1234567890")

        #expect(!account.isSignedIn)
        #expect(account.linkSignInError == AuthError.invalidLink.localizedDescription)
    }

    @Test func aLinkWithoutAKeptSecretIsRefusedWithoutAsking() async {
        let account = makeAccount()

        await account.completeLinkSignIn(token: "the-token-from-the-email-1234567890")

        #expect(account.linkSignInError == AuthError.invalidLink.localizedDescription)
        #expect(server.requests.isEmpty)
    }

    @Test func theSheetShowsLinkSentAndClosesWhenTheLinkSignsIn() async throws {
        server.handler = { request in
            request.path == "/api/auth/email-link" ? .empty(204) : tokenReply(access: "A1", refresh: "R1")
        }
        let account = makeAccount()
        var resumed = 0
        let model = SignInModel(account: account, googleClientID: nil, onSignedIn: { resumed += 1 })
        model.email = "learner@example.com"

        await model.sendLink()
        #expect(model.step == .linkSent)

        await account.completeLinkSignIn(token: "the-token-from-the-email-1234567890")
        #expect(model.isAccountSignedIn)
        await model.accountSignedInElsewhere()
        await model.accountSignedInElsewhere() // only once
        #expect(model.didSignIn)
        #expect(resumed == 1)

        model.backToEmail()
        #expect(model.step == .email)
    }

    // MARK: - Deep link

    @Test func signInLinksAreReadFromBothAddresses() throws {
        let token = "abcdefghijklmnopqrstuvwxyz_-0123456789ABCDE"
        #expect(DeepLink.signInToken(from: try #require(URL(string: "saynata://sign-in?token=\(token)"))) == token)
        #expect(DeepLink.signInToken(from: try #require(URL(string: "https://api.example.com/auth/link?token=\(token)"))) == token)
        #expect(DeepLink.signInToken(from: try #require(URL(string: "saynata://lookup?token=\(token)"))) == nil)
        #expect(DeepLink.signInToken(from: try #require(URL(string: "saynata://sign-in?token=%3Cscript%3E"))) == nil)
        #expect(DeepLink.signInToken(from: try #require(URL(string: "http://api.example.com/auth/link?token=\(token)"))) == nil)
    }

    // MARK: - Google and Apple

    @Test func googleSignInSendsTheIDTokenAndTheRawNonceToTheBackend() async throws {
        server.handler = { request in
            switch request.path {
            case "/token": .json(#"{"id_token":"google-id-token","access_token":"x"}"#)
            case "/api/auth/google": tokenReply(access: "A1", refresh: "R1")
            default: .empty(404)
            }
        }
        var flow = GoogleSignInFlow(clientID: "123-abc.apps.googleusercontent.com", session: server.session)
        flow.tokenEndpoint = server.baseURL.appending(path: "token")

        #expect(flow.callbackScheme == "com.googleusercontent.apps.123-abc")
        let page = try #require(URLComponents(url: flow.authorizationURL, resolvingAgainstBaseURL: false))
        let query = Dictionary(uniqueKeysWithValues: (page.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(query["nonce"] == SignInSecrets.sha256Hex(flow.nonce))
        #expect(query["code_challenge"] == SignInSecrets.sha256Base64URL(flow.codeVerifier))
        #expect(query["redirect_uri"] == "com.googleusercontent.apps.123-abc:/oauthredirect")

        let idToken = try await flow.idToken { _, scheme in
            URL(string: "\(scheme):/oauthredirect?state=\(flow.state)&code=the-code")!
        }
        #expect(idToken == "google-id-token")
        let form = String(decoding: try #require(server.requests(to: "/token").first).body, as: UTF8.self)
        #expect(form.contains("code_verifier=\(flow.codeVerifier)"))

        let account = makeAccount()
        try await account.signIn(with: .google, idToken: idToken, nonce: flow.nonce)
        #expect(account.isSignedIn)
        let body = try #require(server.requests(to: "/api/auth/google").first).json
        #expect(body["idToken"] as? String == "google-id-token")
        #expect(body["nonce"] as? String == flow.nonce)
    }

    @Test func aGoogleAnswerForAnotherSignInIsRefused() throws {
        let flow = GoogleSignInFlow(clientID: "123-abc.apps.googleusercontent.com")
        let wrongState = try #require(URL(string: "com.googleusercontent.apps.123-abc:/oauthredirect?state=other&code=c"))
        #expect(throws: GoogleSignInFlow.FlowError.badCallback) { try flow.code(fromCallback: wrongState) }
    }

    @Test func googleWithoutAClientIDSaysItIsNotSetUp() async {
        let model = SignInModel(account: makeAccount(), googleClientID: nil)

        await model.signInWithGoogle { _, _ in Issue.record("must not open Google"); throw CancellationError() }

        #expect(model.errorMessage == AuthError.providerNotConfigured.localizedDescription)
    }

    @Test func appleSendsTheRawNonceOfThisRequest() async throws {
        server.handler = { _ in tokenReply(access: "A1", refresh: "R1") }
        var resumed = false
        let model = SignInModel(account: makeAccount(), googleClientID: nil, onSignedIn: { resumed = true })

        let hashed = model.prepareAppleRequest()
        await model.finishApple(idToken: "apple-id-token", error: nil, isCancel: false)

        #expect(model.didSignIn)
        #expect(resumed)
        let body = try #require(server.requests(to: "/api/auth/apple").first).json
        #expect(body["idToken"] as? String == "apple-id-token")
        #expect(SignInSecrets.sha256Hex(try #require(body["nonce"] as? String)) == hashed)
    }

    @Test func closingTheAppleSheetShowsNothingAndARefusalShowsTheMessage() async {
        server.handler = { _ in .problem(401, code: "invalid_id_token", detail: "Sign-in did not work. Try again.") }
        let model = SignInModel(account: makeAccount(), googleClientID: nil)
        _ = model.prepareAppleRequest()

        await model.finishApple(idToken: nil, error: CancellationError(), isCancel: true)
        #expect(model.errorMessage == nil)

        await model.finishApple(idToken: "apple-id-token", error: nil, isCancel: false)
        #expect(model.errorMessage == "Sign-in did not work. Try again.")
        #expect(!model.didSignIn)
    }

    @Test func aBackendWithoutGoogleSetUpGivesAClearMessage() {
        let error = AuthClient.error(status: 503, problem: ProblemBody(detail: "x", code: "provider_not_configured"),
                                     retryAfter: nil, path: "api/auth/google")
        #expect(error == .providerNotConfigured)
    }
}
