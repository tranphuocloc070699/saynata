//
//  SettingsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The Settings screen (TASK-29). Today it has one section: how the target-language row opens.
struct SettingsView: View {
    /// The picked language. It names the row in the footer.
    var targetLanguage: TargetLanguage = .vietnamese

    @AppStorage(TranslationRowPreference.modeKey) private var modeRaw = TranslationRowMode.collapsed.rawValue
    @Environment(\.dismiss) private var dismiss
    @Environment(AccountStore.self) private var account
    @State private var isSignInOpen = false
    @State private var isDeleteConfirmationOpen = false
    /// Why "Delete account" failed, shown under the section.
    @State private var accountError: String?

    private var mode: TranslationRowMode { TranslationRowMode(rawValue: modeRaw) ?? .collapsed }

    /// Uses the language name when there is a row to open. English has no target row.
    private var footerText: String {
        let name = targetLanguage.hasTargetLanguageRow ? targetLanguage.title : "translation"
        return "How the \(name) row opens on words and sentences."
    }

    var body: some View {
        NavigationStack {
            List {
                accountSection

                Section {
                    ForEach(TranslationRowMode.allCases) { option in
                        Button {
                            modeRaw = option.rawValue
                        } label: {
                            HStack {
                                Text(option.title)
                                    .foregroundStyle(Theme.Colors.textPrimary)
                                Spacer()
                                if option == mode {
                                    Image(systemName: "checkmark")
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(Theme.Colors.accent)
                                }
                            }
                            .frame(minHeight: Theme.minTapTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(option == mode ? .isSelected : [])
                    }
                } header: {
                    Text("Show translation")
                } footer: {
                    Text(footerText)
                }
                .listRowBackground(Theme.Colors.surface)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.Colors.background)
            .navigationTitle("Settings")
            .inlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .sheetTrailing) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
            // On the NavigationStack, not on the Account section: a sheet on a List row is closed again when the list
            // redraws its rows (the keyboard opening in the sheet did that), so Sign in closed itself at once.
            .sheet(isPresented: $isSignInOpen) {
                SignInSheet(account: account)
            }
            .confirmationDialog("Delete your account?", isPresented: $isDeleteConfirmationOpen, titleVisibility: .visible) {
                Button("Delete account", role: .destructive) {
                    Task { await deleteAccount() }
                }
            } message: {
                Text("Your account is removed from our server and you are signed out. This cannot be undone.")
            }
        }
    }
}

// MARK: - Account (TASK-70)

extension SettingsView {
    /// Signed out: one "Sign in" row. Signed in: the email, "Sign out" and "Delete account".
    /// These are list rows, not buttons with labels, so they keep their text (CLAUDE.md "UI rule").
    @ViewBuilder
    fileprivate var accountSection: some View {
        Section {
            if let user = account.user {
                Label(user.email, systemImage: "person.crop.circle")
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .frame(minHeight: Theme.minTapTarget, alignment: .leading)
                accountRow("Sign out", color: Theme.Colors.textPrimary) {
                    Task { await account.signOut() }
                }
                accountRow("Delete account", color: Theme.Colors.error) {
                    isDeleteConfirmationOpen = true
                }
            } else {
                accountRow("Sign in", color: Theme.Colors.textPrimary) { isSignInOpen = true }
            }
        } header: {
            Text("Account")
        } footer: {
            if let accountError {
                Label(accountError, systemImage: "exclamationmark.circle")
                    .foregroundStyle(Theme.Colors.error)
            } else if !account.isSignedIn {
                Text("Saved words work without an account. New sentences need one.")
            }
        }
        .listRowBackground(Theme.Colors.surface)
    }

    private func accountRow(_ title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .foregroundStyle(color)
                .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func deleteAccount() async {
        accountError = nil
        do {
            try await account.deleteAccount()
        } catch {
            accountError = error.localizedDescription
        }
    }
}

#Preview {
    SettingsView()
        .environment(AccountStore(secrets: NoSecretStore()))
}
