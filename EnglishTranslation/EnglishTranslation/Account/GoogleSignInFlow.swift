//
//  GoogleSignInFlow.swift
//  EnglishTranslation
//

import Foundation

/// Sign in with Google without the Google SDK (TASK-92): the system web sign-in sheet
/// (`ASWebAuthenticationSession`, through SwiftUI's `webAuthenticationSession`) shows Google's page, Google sends
/// back a code, and the app swaps the code for an ID token. This is OAuth for native apps (RFC 8252) with PKCE,
/// the way Google documents it for an iOS client: https://developers.google.com/identity/protocols/oauth2/native-app
///
/// The ID token then goes to our backend (`POST /api/auth/google`), which checks it again. Google's client ID for
/// iOS has no secret, so nothing secret is in the app.
struct GoogleSignInFlow: Sendable {
    /// The iOS OAuth client ID from Google Cloud ("123-abc.apps.googleusercontent.com"), `GoogleIOSClientID` in Info.plist.
    let clientID: String
    /// One sign-in's random values. A new flow makes new ones.
    var state = SignInSecrets.random()
    var codeVerifier = SignInSecrets.random()
    var nonce = SignInSecrets.random()
    var session: URLSession = .shared
    /// Where the code is swapped for tokens. Tests point it at a fake server.
    var tokenEndpoint = URL(string: "https://oauth2.googleapis.com/token")!

    static let authorizationEndpoint = URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!

    enum FlowError: LocalizedError, Equatable {
        case badCallback
        case noIDToken

        var errorDescription: String? { "Google sign-in did not work. Try again." }
    }

    /// Google's "reversed client ID": the iOS client's redirect scheme ("com.googleusercontent.apps.123-abc").
    var callbackScheme: String {
        clientID.split(separator: ".").reversed().joined(separator: ".")
    }

    var redirectURI: String { "\(callbackScheme):/oauthredirect" }

    /// The Google page to open. `nonce` is sent as its SHA-256, like Apple, so the backend checks both the same way.
    var authorizationURL: URL {
        var components = URLComponents(url: Self.authorizationEndpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email"),
            URLQueryItem(name: "code_challenge", value: SignInSecrets.sha256Base64URL(codeVerifier)),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "nonce", value: SignInSecrets.sha256Hex(nonce)),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        return components.url!
    }

    /// The code in Google's answer. Throws when the state is not ours (another sign-in) or there is no code.
    func code(fromCallback url: URL) throws -> String {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard url.scheme == callbackScheme,
              items.first(where: { $0.name == "state" })?.value == state,
              let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty
        else { throw FlowError.badCallback }
        return code
    }

    /// The form that swaps the code for tokens.
    func tokenRequest(code: String) -> URLRequest {
        var request = URLRequest(url: tokenEndpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var form = URLComponents()
        form.queryItems = [
            URLQueryItem(name: "code", value: code),
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "grant_type", value: "authorization_code"),
            URLQueryItem(name: "code_verifier", value: codeVerifier),
        ]
        request.httpBody = Data((form.percentEncodedQuery ?? "").utf8)
        return request
    }

    /// Runs the whole flow and returns Google's ID token. `authenticate` shows the web sheet and returns the
    /// callback URL (SwiftUI's `webAuthenticationSession`; tests pass a fake).
    func idToken(authenticate: (URL, String) async throws -> URL) async throws -> String {
        let callback = try await authenticate(authorizationURL, callbackScheme)
        let code = try code(fromCallback: callback)
        let (data, response) = try await session.data(for: tokenRequest(code: code))
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let body = try? JSONDecoder().decode(TokenBody.self, from: data),
              let idToken = body.id_token, !idToken.isEmpty
        else { throw FlowError.noIDToken }
        return idToken
    }

    private struct TokenBody: Decodable {
        let id_token: String?   // swiftlint:disable:this identifier_name
    }
}
