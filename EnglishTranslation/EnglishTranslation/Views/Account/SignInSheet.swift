//
//  SignInSheet.swift
//  EnglishTranslation
//

#if os(iOS)
import AuthenticationServices
#endif
import SwiftUI

/// The sign-in bottom sheet (TASK-70, option A of the 2026-10-15 mockup). It opens over the result when the backend
/// answers `sign_in_required`, and from Settings > Account > Sign in.
/// Step 1: an email and a round send button. Step 2: six boxes for the code. No button has a visible label (CLAUDE.md).
/// TASK-92 adds, on iOS only, "Email me a link", Sign in with Apple and Sign in with Google under step 1
/// (`OtherSignInOptions`). That part is a plain first version the developer will redesign, so it has no mockup;
/// the Apple and Google buttons keep their words because both providers' brand rules ask for them.
struct SignInSheet: View {
    @State private var model: SignInModel
    @Environment(\.dismiss) private var dismiss

    @FocusState private var isEmailFocused: Bool

    /// - Parameter onSignedIn: runs after the code was right. The sheet then closes by itself.
    ///   For a result that was waiting, this runs the request again.
    init(account: AccountStore, onSignedIn: @escaping () async -> Void = {}) {
        _model = State(initialValue: SignInModel(account: account, onSignedIn: onSignedIn))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                switch model.step {
                case .email:
                    emailStep
                    #if os(iOS)
                    OtherSignInOptions(model: model)
                    #endif
                case .code: codeStep
                case .linkSent: linkSentStep
                }
                messages
            }
            .padding(.horizontal, Theme.Spacing.large)
            .padding(.top, Theme.Spacing.xLarge)
            .padding(.bottom, Theme.Spacing.large)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Theme.Colors.background)
        #if os(macOS)
        // A Mac sheet cannot be dragged down, so it gets a Close button (Esc too).
        .overlay(alignment: .topTrailing) {
            IconButton(systemImage: "xmark", label: "Close") { dismiss() }
                .keyboardShortcut(.cancelAction)
                .padding(Theme.Spacing.medium)
        }
        #endif
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .animation(.default, value: model.step)
        .task { isEmailFocused = true }
        .onChange(of: model.didSignIn) { _, signedIn in
            if signedIn { dismiss() }
        }
        // The emailed link opened the app and signed in (EnglishTranslationApp.onOpenURL): close, and run the
        // waiting request again.
        .onChange(of: model.isAccountSignedIn) { _, signedIn in
            if signedIn { Task { await model.accountSignedInElsewhere() } }
        }
    }

    // MARK: - Step 1

    private var emailStep: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            Text("Sign in to explain new sentences")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Theme.Colors.textPrimary)
            Text("Saved words still work without an account.")
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)

            HStack(spacing: Theme.Spacing.medium) {
                TextField("Email", text: $model.email)
                    .textFieldStyle(.plain)
                    .emailFieldStyle()
                    .autocorrectionDisabled()
                    .submitLabel(.send)
                    .focused($isEmailFocused)
                    .onSubmit { Task { await model.sendCode() } }
                    .padding(.horizontal, Theme.Spacing.large)
                    .frame(minHeight: Theme.minTapTarget + Theme.Spacing.small)
                    .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
                    .overlay {
                        RoundedRectangle(cornerRadius: Theme.Radius.block)
                            .strokeBorder(isEmailFocused ? Theme.Colors.accent : Theme.Colors.hairline,
                                          lineWidth: isEmailFocused ? 2 : 1)
                    }
                IconButton(systemImage: "arrow.up", label: "Send code", isProminent: true) {
                    Task { await model.sendCode() }
                }
                .disabled(!model.canSendCode)
                .opacity(model.canSendCode ? 1 : 0.4)
            }
        }
    }

    // MARK: - Step 2

    private var codeStep: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            Text("Enter the code")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Theme.Colors.textPrimary)
            (Text("We sent 6 numbers to ") + Text(model.sentTo).fontWeight(.semibold))
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)

            CodeBoxes(
                code: Binding(
                    get: { model.code },
                    set: { value in
                        // Saved at once, then checked: a late save lost a code filled from Mail.
                        if model.setCode(value) { Task { await model.verifyCode() } }
                    }),
                length: SignInModel.codeLength)
                .disabled(model.isBusy)

            TextLinkButton(title: "Send a new code") { Task { await model.sendNewCode() } }
                .disabled(model.isBusy)
        }
    }

    // MARK: - Link sent

    private var linkSentStep: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            Text("Check your email")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Theme.Colors.textPrimary)
            (Text("We sent a sign-in link to ") + Text(model.sentTo).fontWeight(.semibold)
                + Text(". Open it on this iPhone."))
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)
            TextLinkButton(title: "Send a new link") { Task { await model.sendLink() } }
                .disabled(model.isBusy)
            TextLinkButton(title: "Use a code instead") { model.backToEmail() }
        }
    }

    // MARK: - Messages

    @ViewBuilder
    private var messages: some View {
        if let error = model.errorMessage {
            Label(error, systemImage: "exclamationmark.circle")
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.error)
        } else if let notice = model.noticeMessage {
            Text(notice)
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)
        }
    }
}

/// An underlined text row, like "Send a new code". A row of words, not an icon button (CLAUDE.md allows it here).
private struct TextLinkButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .underline()
                .foregroundStyle(Theme.Colors.textSecondary)
                .frame(minHeight: Theme.minTapTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

#if os(iOS)
/// "Email me a link", Sign in with Apple, Sign in with Google (TASK-92). A plain first version, easy to replace.
private struct OtherSignInOptions: View {
    let model: SignInModel
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            TextLinkButton(title: "Email me a link instead") { Task { await model.sendLink() } }
                .disabled(!model.canSendCode)

            Text("or")
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)
                .frame(maxWidth: .infinity)

            SignInWithAppleButton(.signIn) { request in
                request.requestedScopes = [.email]
                request.nonce = model.prepareAppleRequest()
            } onCompletion: { result in
                Task { await finishApple(result) }
            }
            .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
            .frame(height: Theme.minTapTarget + Theme.Spacing.small)
            .disabled(model.isBusy)

            Button {
                Task {
                    await model.signInWithGoogle { url, scheme in
                        try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: scheme)
                    }
                }
            } label: {
                Text("Sign in with Google")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget + Theme.Spacing.small)
                    .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
                    .overlay {
                        RoundedRectangle(cornerRadius: Theme.Radius.block).strokeBorder(Theme.Colors.hairline)
                    }
            }
            .buttonStyle(.plain)
            .disabled(model.isBusy)
        }
    }

    private func finishApple(_ result: Result<ASAuthorization, Error>) async {
        switch result {
        case let .success(authorization):
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential
            let token = credential?.identityToken.flatMap { String(data: $0, encoding: .utf8) }
            await model.finishApple(idToken: token, error: nil, isCancel: false)
        case let .failure(error):
            let isCancel = (error as? ASAuthorizationError)?.code == .canceled
            await model.finishApple(idToken: nil, error: error, isCancel: isCancel)
        }
    }
}
#endif

#Preview("Email") {
    SignInSheet(account: AccountStore(secrets: NoSecretStore()))
}
