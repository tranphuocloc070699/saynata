//
//  SignInModel.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// The logic of the sign-in sheet: step 1 asks for an email, step 2 asks for the 6-digit code.
/// Step 1 also offers Google, Apple, and "Email me a link" (TASK-92); a sent link shows the `.linkSent` step.
/// The sheet (`SignInSheet`) only draws it.
@Observable
final class SignInModel {
    enum Step: Equatable {
        case email
        case code
        /// The sign-in link is on its way. The app signs in when the link opens it.
        case linkSent
    }

    /// The backend makes 6-digit codes.
    static let codeLength = 6
    /// The backend locks a code after 5 wrong tries (doc-17), so the sheet says so on the 5th.
    static let maxWrongTries = 5

    private(set) var step: Step = .email
    var email = ""
    /// Digits only, at most `codeLength`.
    private(set) var code = ""
    private(set) var isBusy = false
    /// A red line under the field. Nil when all is fine.
    private(set) var errorMessage: String?
    /// A neutral line, for example "We sent a new code."
    private(set) var noticeMessage: String?
    private(set) var wrongTries = 0
    /// True once the code was right. The sheet closes when it turns true.
    private(set) var didSignIn = false

    private let account: AccountStore
    /// Called once, after the code was right and the tokens are saved. The sheet closes and the request runs again.
    private let onSignedIn: () async -> Void
    /// The iOS OAuth client ID for Google, or nil when Info.plist has none yet (the button then says so).
    private let googleClientID: String?
    /// The raw nonce of the Apple sign-in that is running. Apple gets only its hash.
    @ObservationIgnored private var appleNonce: String?

    init(
        account: AccountStore,
        googleClientID: String? = AppConfig.googleIOSClientID(),
        onSignedIn: @escaping () async -> Void = {}
    ) {
        self.account = account
        self.googleClientID = googleClientID
        self.onSignedIn = onSignedIn
    }

    /// True once the account is signed in, by this sheet or by a link that opened the app.
    var isAccountSignedIn: Bool { account.isSignedIn }

    private var trimmedEmail: String {
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// A light check, so an obvious typo does not use one of the 3 codes per 15 minutes. The server checks again.
    var isEmailValid: Bool {
        trimmedEmail.range(of: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", options: .regularExpression) != nil
    }

    var canSendCode: Bool {
        isEmailValid && !isBusy
    }

    /// The email the code was sent to, as typed (trimmed). Shown in step 2.
    var sentTo: String { trimmedEmail }

    // MARK: - Step 1

    /// Asks the backend to email a code. On success the sheet moves to step 2.
    func sendCode() async {
        guard canSendCode else { return }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        defer { isBusy = false }
        do {
            try await account.requestCode(email: trimmedEmail)
            code = ""
            wrongTries = 0
            step = .code
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// "Email me a link". On success the sheet shows `.linkSent`.
    func sendLink() async {
        guard canSendCode else { return }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        defer { isBusy = false }
        do {
            try await account.requestLink(email: trimmedEmail)
            step = .linkSent
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Back to step 1, for "Use a code instead" and "Change email".
    func backToEmail() {
        step = .email
        errorMessage = nil
        noticeMessage = nil
    }

    // MARK: - Google and Apple

    /// Signs in with Google. `authenticate` shows Google's page in the system web sheet and returns the callback URL
    /// (SwiftUI's `webAuthenticationSession`); a closed sheet throws a cancel error, which shows nothing.
    func signInWithGoogle(authenticate: (URL, String) async throws -> URL) async {
        guard !isBusy else { return }
        guard let googleClientID else {
            errorMessage = AuthError.providerNotConfigured.localizedDescription
            return
        }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        let flow = GoogleSignInFlow(clientID: googleClientID)
        do {
            let idToken = try await flow.idToken(authenticate: authenticate)
            try await account.signIn(with: .google, idToken: idToken, nonce: flow.nonce)
            finish()
        } catch {
            isBusy = false
            if !Self.isCancel(error) { errorMessage = error.localizedDescription }
            return
        }
        await onSignedIn()
    }

    /// Called when the Apple button is tapped: returns the hashed nonce to put in Apple's request.
    func prepareAppleRequest() -> String {
        let nonce = SignInSecrets.random()
        appleNonce = nonce
        return SignInSecrets.sha256Hex(nonce)
    }

    /// Apple answered. `idToken` is the credential's identity token; `error` is set when Apple failed or the user
    /// closed the sheet (`isCancel` true: nothing is shown).
    func finishApple(idToken: String?, error: Error?, isCancel: Bool) async {
        guard !isBusy else { return }
        if isCancel { return }
        guard error == nil, let idToken, let nonce = appleNonce else {
            errorMessage = "Sign in with Apple did not work. Try again, or use your email."
            return
        }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        do {
            try await account.signIn(with: .apple, idToken: idToken, nonce: nonce)
            appleNonce = nil
            finish()
        } catch {
            isBusy = false
            errorMessage = error.localizedDescription
            return
        }
        await onSignedIn()
    }

    /// The account was signed in outside this sheet (the emailed link opened the app). Closes the sheet once.
    func accountSignedInElsewhere() async {
        guard !didSignIn, account.isSignedIn else { return }
        finish()
        await onSignedIn()
    }

    private func finish() {
        isBusy = false
        didSignIn = true
    }

    /// The user closed Google's web sheet. `ASWebAuthenticationSessionError.canceledLogin` is code 1 in its domain.
    static func isCancel(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == "com.apple.AuthenticationServices.WebAuthenticationSession" && nsError.code == 1
            || error is CancellationError
    }

    // MARK: - Step 2

    /// "Send a new code". The old code stops working, and the tries start again.
    func sendNewCode() async {
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        defer { isBusy = false }
        do {
            try await account.requestCode(email: trimmedEmail)
            code = ""
            wrongTries = 0
            noticeMessage = "We sent a new code."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// The text field changed (typing, or Mail filling the code). Keeps digits only, and checks the code at 6 digits.
    func updateCode(_ raw: String) async {
        if setCode(raw) {
            await verify()
        }
    }

    /// Saves the digits at once (not in a Task), so the text field reads them back right away. When Mail fills all
    /// 6 digits in one go, a late update made SwiftUI put the old empty text back, and the code vanished.
    /// Returns true when the code is complete and should be checked.
    @discardableResult
    func setCode(_ raw: String) -> Bool {
        code = String(raw.filter(\.isNumber).prefix(Self.codeLength))
        return code.count == Self.codeLength
    }

    /// Checks a complete code. Call after `setCode` returned true.
    func verifyCode() async {
        guard code.count == Self.codeLength else { return }
        await verify()
    }

    private func verify() async {
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        noticeMessage = nil
        do {
            try await account.signIn(email: trimmedEmail, code: code)
            finish()
            await onSignedIn()
        } catch AuthError.invalidCode {
            isBusy = false
            wrongTries += 1
            code = ""
            errorMessage = wrongTries >= Self.maxWrongTries
                ? "Too many tries. Send a new code."
                : "That code is wrong or has expired. Try again, or send a new code."
        } catch {
            isBusy = false
            code = ""
            errorMessage = error.localizedDescription
        }
    }
}
