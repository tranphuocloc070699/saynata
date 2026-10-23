//
//  SignInSheet.swift
//  EnglishTranslation
//

#if os(iOS)
import AuthenticationServices
#endif
import SwiftUI

/// The sign-in bottom sheet (TASK-70, redone in TASK-96 with the cat room, mockup A of 2026-10-23).
/// It opens over the result when the backend answers `sign_in_required`, and from Settings > Account > Sign in.
/// iOS first screen: the cat room, "Welcome to Saynata", two equal pills (Apple, Google) and a "Use email instead"
/// link. The link shows the email step: an email field and a round send button, then six boxes for the code
/// (or a link was sent). The Mac has no Apple or Google button, so it goes straight to the email step.
/// No icon button has a label. The Apple and Google pills keep their words because both providers' brand rules
/// ask for them.
struct SignInSheet: View {
    @State private var model: SignInModel
    @Environment(\.dismiss) private var dismiss

    @FocusState private var isEmailFocused: Bool
    /// iOS starts with the provider buttons; "Use email instead" turns this on. The Mac has no providers.
    @State private var showsEmailForm: Bool = SignInSheet.startsWithEmailForm

    private static var startsWithEmailForm: Bool {
        #if os(iOS)
        false
        #else
        true
        #endif
    }

    /// - Parameter onSignedIn: runs after the code was right. The sheet then closes by itself.
    ///   For a result that was waiting, this runs the request again.
    init(account: AccountStore, onSignedIn: @escaping () async -> Void = {}) {
        _model = State(initialValue: SignInModel(account: account, onSignedIn: onSignedIn))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: -Theme.Spacing.large) {
                SignInCatRoom()
                content
                    .padding(.horizontal, Theme.Spacing.large)
                    .padding(.top, Theme.Spacing.xLarge)
                    .padding(.bottom, Theme.Spacing.large)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.Colors.background, in: UnevenRoundedRectangle(
                        topLeadingRadius: Theme.Radius.card, topTrailingRadius: Theme.Radius.card))
            }
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
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .animation(.default, value: model.step)
        .animation(.default, value: showsEmailForm)
        #if os(macOS)
        .frame(width: 420, height: 600)
        #endif
        .task { isEmailFocused = showsEmailForm }
        .onChange(of: showsEmailForm) { _, shows in isEmailFocused = shows }
        .onChange(of: model.didSignIn) { _, signedIn in
            if signedIn { dismiss() }
        }
        // The emailed link opened the app and signed in (EnglishTranslationApp.onOpenURL): close, and run the
        // waiting request again.
        .onChange(of: model.isAccountSignedIn) { _, signedIn in
            if signedIn { Task { await model.accountSignedInElsewhere() } }
        }
    }

    // MARK: - Content

    private var content: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            switch model.step {
            case .email:
                Text("Welcome to Saynata")
                    .font(.system(.title, design: .serif))
                    .foregroundStyle(Theme.Colors.textPrimary)
                if showsEmailForm {
                    emailStep
                } else {
                    #if os(iOS)
                    ProviderButtons(model: model) { showsEmailForm = true }
                    #endif
                }
            case .code: codeStep
            case .linkSent: linkSentStep
            }
            messages
        }
    }

    // MARK: - Step 1: email

    private var emailStep: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
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
            #if os(iOS)
            HStack {
                IconButton(systemImage: "chevron.left", label: "Other ways to sign in", style: .plain) {
                    showsEmailForm = false
                }
                Spacer()
                TextLinkButton(title: "Email me a link instead") { Task { await model.sendLink() } }
                    .disabled(!model.canSendCode)
            }
            #endif
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
/// Sign in with Apple, Sign in with Google and the "Use email instead" link (TASK-92, restyled in TASK-96).
/// The two buttons are equal full-width pills: Apple black, Google white with a hairline border.
/// Pure black and white are the one exception to "colors only from Theme": the providers' brand rules ask for them.
private struct ProviderButtons: View {
    let model: SignInModel
    let useEmail: () -> Void
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession

    private let pillHeight = Theme.minTapTarget + Theme.Spacing.small

    var body: some View {
        VStack(spacing: Theme.Spacing.medium) {
            SignInWithAppleButton(.signIn) { request in
                request.requestedScopes = [.email]
                request.nonce = model.prepareAppleRequest()
            } onCompletion: { result in
                Task { await finishApple(result) }
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: pillHeight)
            .clipShape(Capsule())
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
                    .foregroundStyle(Color.black)
                    .frame(maxWidth: .infinity, minHeight: pillHeight)
                    .background(Color.white, in: Capsule())
                    .overlay { Capsule().strokeBorder(Theme.Colors.hairline) }
            }
            .buttonStyle(.plain)
            .disabled(model.isBusy)

            Button(action: useEmail) {
                Text("Use email instead")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.Colors.accent)
                    .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
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
