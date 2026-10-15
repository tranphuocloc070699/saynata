//
//  SignInModel.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// The logic of the sign-in sheet: step 1 asks for an email, step 2 asks for the 6-digit code.
/// The sheet (`SignInSheet`) only draws it.
@Observable
final class SignInModel {
    enum Step: Equatable {
        case email
        case code
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

    init(account: AccountStore, onSignedIn: @escaping () async -> Void = {}) {
        self.account = account
        self.onSignedIn = onSignedIn
    }

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
            isBusy = false
            didSignIn = true
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
