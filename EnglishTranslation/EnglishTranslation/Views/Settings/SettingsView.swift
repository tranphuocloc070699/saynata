//
//  SettingsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The Settings screen (TASK-29, cozy cards in TASK-98): a profile card with the cat, the "Translation" card
/// (how the target-language row opens) and an About card.
/// The Mac app adds its own sections at the end (shortcut, launch at login) through `moreSections` (TASK-85.3).
struct SettingsView<MoreSections: View>: View {
    /// The picked language. Kept so the Mac app can pass it; the row titles are the same for every language.
    var targetLanguage: TargetLanguage = .vietnamese
    /// Sections shown after the shared ones. The iPhone app has none.
    @ViewBuilder var moreSections: () -> MoreSections

    @AppStorage(TranslationRowPreference.modeKey) private var modeRaw = TranslationRowMode.collapsed.rawValue
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(AccountStore.self) private var account
    @State private var isSignInOpen = false
    @State private var isDeleteConfirmationOpen = false
    @State private var isAboutOpen = false
    /// Why "Delete account" failed, shown in the profile card.
    @State private var accountError: String?

    /// The project page, opened by "Source code".
    static var sourceCodeURL: URL { URL(string: "https://github.com/tranphuocloc070699/saynata")! }

    private static var avatarSize: CGFloat { 52 }

    private var mode: TranslationRowMode { TranslationRowMode(rawValue: modeRaw) ?? .collapsed }

    var body: some View {
        NavigationStack {
            // A List (not a ScrollView) so the Mac sections from `moreSections` still work. Each card is one row
            // with no background of its own; the card draws its own surface.
            List {
                profileCard
                    .cardRow()

                Text("Translation")
                    .sectionCaption()
                    .accessibilityAddTraits(.isHeader)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Theme.Spacing.small, leading: Theme.Spacing.xLarge,
                                              bottom: 0, trailing: Theme.Spacing.large))
                translationCard
                    .cardRow()

                aboutCard
                    .cardRow()

                moreSections()
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
            // On the NavigationStack, not on a row: a sheet on a List row is closed again when the list
            // redraws its rows (the keyboard opening in the sheet did that), so Sign in closed itself at once.
            .navigationDestination(isPresented: $isAboutOpen) {
                AboutView()
            }
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

extension SettingsView where MoreSections == EmptyView {
    init(targetLanguage: TargetLanguage = .vietnamese) {
        self.init(targetLanguage: targetLanguage, moreSections: { EmptyView() })
    }
}

// MARK: - Cards (TASK-98)

extension SettingsView {
    /// Signed out: the cat, "Sign in" and a hint; the whole card opens the sign-in sheet.
    /// Signed in: the cat, the email, then "Sign out" and "Delete account" (TASK-70).
    /// These are list rows with text, not buttons with labels, so they keep their text (CLAUDE.md "UI rule").
    @ViewBuilder
    fileprivate var profileCard: some View {
        if let user = account.user {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: Theme.Spacing.medium) {
                    avatar
                    Text(user.email)
                        .font(.body)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Spacer(minLength: 0)
                }
                .padding(Theme.Spacing.large)
                HairlineDivider()
                textRow("Sign out", color: Theme.Colors.textPrimary) {
                    Task { await account.signOut() }
                }
                HairlineDivider()
                textRow("Delete account", color: Theme.Colors.error) {
                    isDeleteConfirmationOpen = true
                }
                if let accountError {
                    HairlineDivider()
                    Label(accountError, systemImage: "exclamationmark.circle")
                        .font(.footnote)
                        .foregroundStyle(Theme.Colors.error)
                        .padding(Theme.Spacing.large)
                }
            }
            .settingsCard()
        } else {
            Button { isSignInOpen = true } label: {
                HStack(spacing: Theme.Spacing.medium) {
                    avatar
                    VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                        Text("Sign in")
                            .font(.body)
                            .foregroundStyle(Theme.Colors.textPrimary)
                        Text("To explain new sentences")
                            .font(.footnote)
                            .foregroundStyle(Theme.Colors.textMuted)
                    }
                    Spacer(minLength: 0)
                    chevron
                }
                .padding(Theme.Spacing.large)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .settingsCard()
        }
    }

    /// The cat in a soft circle. It is a still picture with Reduce Motion or when the assets are missing.
    private var avatar: some View {
        ZStack {
            Circle().fill(Theme.Colors.chipFill)
            CatSpriteView(sheet: .idle, size: CGSize(width: 32, height: 32))
        }
        .frame(width: Self.avatarSize, height: Self.avatarSize)
    }

    /// Three rows: how the target-language row opens on results.
    fileprivate var translationCard: some View {
        VStack(spacing: 0) {
            ForEach(Array(TranslationRowMode.allCases.enumerated()), id: \.element) { index, option in
                if index > 0 { HairlineDivider() }
                Button {
                    modeRaw = option.rawValue
                } label: {
                    HStack(spacing: Theme.Spacing.medium) {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                            Text(option.title)
                                .font(.body)
                                .foregroundStyle(Theme.Colors.textPrimary)
                            Text(option.hint)
                                .font(.footnote)
                                .foregroundStyle(Theme.Colors.textMuted)
                        }
                        Spacer(minLength: 0)
                        if option == mode {
                            Image(systemName: "checkmark")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Theme.Colors.accent)
                        }
                    }
                    .padding(.horizontal, Theme.Spacing.large)
                    .padding(.vertical, Theme.Spacing.medium)
                    .frame(maxWidth: .infinity, minHeight: Theme.minTapTarget, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(option == mode ? .isSelected : [])
            }
        }
        .settingsCard()
    }

    /// "About Saynata" (a page) and "Source code" (the GitHub project).
    fileprivate var aboutCard: some View {
        VStack(spacing: 0) {
            // A Button plus `navigationDestination`, not a NavigationLink: inside a List a NavigationLink adds its own chevron.
            Button { isAboutOpen = true } label: {
                linkRow("About Saynata")
            }
            .buttonStyle(.plain)
            HairlineDivider()
            Button { openURL(Self.sourceCodeURL) } label: {
                linkRow("Source code")
            }
            .buttonStyle(.plain)
        }
        .settingsCard()
    }

    private func linkRow(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.body)
                .foregroundStyle(Theme.Colors.textPrimary)
            Spacer(minLength: 0)
            chevron
        }
        .padding(.horizontal, Theme.Spacing.large)
        .frame(minHeight: Theme.minTapTarget)
        .contentShape(Rectangle())
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Theme.Colors.textMuted)
            .accessibilityHidden(true)
    }

    private func textRow(_ title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body)
                .foregroundStyle(color)
                .padding(.horizontal, Theme.Spacing.large)
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

private extension View {
    /// The card look for a group of rows: surface, hairline border, card radius. The rows add their own padding.
    func settingsCard() -> some View {
        self
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card).strokeBorder(Theme.Colors.hairline, lineWidth: 1)
            }
    }

    /// Makes a card fill a List row with no row background, separator or inset of its own.
    func cardRow() -> some View {
        self
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Theme.Spacing.small, leading: Theme.Spacing.large,
                                      bottom: Theme.Spacing.small, trailing: Theme.Spacing.large))
    }

    /// The small uppercase title above a card.
    func sectionCaption() -> some View {
        self
            .font(.caption.weight(.semibold))
            .textCase(.uppercase)
            .foregroundStyle(Theme.Colors.textMuted)
    }
}

#Preview {
    SettingsView()
        .environment(AccountStore(secrets: NoSecretStore()))
}
