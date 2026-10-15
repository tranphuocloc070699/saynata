//
//  SignInSheet.swift
//  EnglishTranslation
//

import SwiftUI

/// The sign-in bottom sheet (TASK-70, option A of the 2026-10-15 mockup). It opens over the result when the backend
/// answers `sign_in_required`, and from Settings > Account > Sign in.
/// Step 1: an email and a round send button. Step 2: six boxes for the code. No button has a visible label (CLAUDE.md).
struct SignInSheet: View {
    @State private var model: SignInModel
    @Environment(\.dismiss) private var dismiss

    @FocusState private var isEmailFocused: Bool
    @FocusState private var isCodeFocused: Bool

    /// - Parameter onSignedIn: runs after the code was right. The sheet then closes by itself.
    ///   For a result that was waiting, this runs the request again.
    init(account: AccountStore, onSignedIn: @escaping () async -> Void = {}) {
        _model = State(initialValue: SignInModel(account: account, onSignedIn: onSignedIn))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            switch model.step {
            case .email: emailStep
            case .code: codeStep
            }
            messages
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Theme.Spacing.large)
        .padding(.top, Theme.Spacing.xLarge)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Colors.background)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .animation(.default, value: model.step)
        .task { isEmailFocused = true }
        .onChange(of: model.didSignIn) { _, signedIn in
            if signedIn { dismiss() }
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
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
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
                code: Binding(get: { model.code }, set: { value in Task { await model.updateCode(value) } }),
                length: SignInModel.codeLength,
                isFocused: $isCodeFocused)
                .disabled(model.isBusy)
                .task { isCodeFocused = true }

            Button {
                Task { await model.sendNewCode() }
            } label: {
                Text("Send a new code")
                    .font(.subheadline.weight(.semibold))
                    .underline()
                    .foregroundStyle(Theme.Colors.textSecondary)
                    .frame(minHeight: Theme.minTapTarget, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(model.isBusy)
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

#Preview("Email") {
    SignInSheet(account: AccountStore(secrets: NoSecretStore()))
}
