//
//  MenuBarContentView.swift
//  SaynataMac
//

import SwiftUI

/// The window under the menu-bar icon (TASK-85.2, mockup option A, about 420 × 560 pt).
///
/// Top: the name, the language picker and the Settings button. Then the input box with the Explain button.
/// Below: the same result views as the iPhone app (`TranslateResultView`), so the order is the product rule:
/// the simple English explanation, then the words, then the target language, folded.
struct MenuBarContentView: View {
    @Environment(TranslationStore.self) private var store
    @Environment(SpeechPlayer.self) private var speechPlayer
    @Environment(AccountStore.self) private var account
    @Environment(LikedStore.self) private var liked
    @Environment(MenuBarFocus.self) private var focus
    @FocusState private var isInputFocused: Bool

    static let size = CGSize(width: 420, height: 560)

    var body: some View {
        @Bindable var store = store
        @Bindable var focus = focus

        VStack(alignment: .leading, spacing: 0) {
            header
                .padding(.horizontal, Theme.Spacing.large)
                .padding(.top, Theme.Spacing.medium)
            inputBox
                .padding(Theme.Spacing.large)
            HairlineDivider()
            ScrollView {
                TranslateResultView(
                    state: store.state,
                    targetLanguage: store.targetLanguage,
                    pendingSentence: store.pendingSentence,
                    onSuggestionTap: { word in Task { await store.lookUp(suggestion: word) } },
                    loadWordEntry: { word in try await store.wordEntry(for: word) })
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .padding(Theme.Spacing.large)
            }
        }
        .frame(width: Self.size.width, height: Self.size.height)
        .background(Theme.Colors.background)
        // A new search replaces the result, so the old speech stops (TASK-46).
        .onChange(of: store.state.resultKey) { speechPlayer.stop() }
        // The shortcut (or the icon) opened the window: put the cursor in the box (TASK-85.3).
        .onChange(of: focus.request) { isInputFocused = true }
        .onAppear { isInputFocused = true }
        .task(id: account.user) {
            if account.isSignedIn { await liked.refresh() } else { liked.clear() }
        }
        // A guest tapped the star (TASK-93.2): open the sign-in sheet, and send the like after sign-in.
        .onChange(of: liked.signInRequested) { _, isRequested in
            guard isRequested else { return }
            liked.signInRequested = false
            store.isSignInSheetOpen = true
        }
        .sheet(isPresented: $store.isSignInSheetOpen, onDismiss: { liked.cancelPending() }) {
            SignInSheet(account: account) {
                await store.resumeAfterSignIn()
                await liked.signedIn()
            }
                .frame(width: Self.size.width - 40, height: 380)
        }
        .sheet(isPresented: $focus.isSettingsOpen) {
            SettingsView(targetLanguage: store.targetLanguage) {
                MacSettingsSections()
            }
            .frame(width: Self.size.width - 40, height: Self.size.height - 60)
        }
    }

    private var header: some View {
        @Bindable var store = store

        return HStack(spacing: Theme.Spacing.small) {
            Text("Saynata")
                .font(.headline)
                .foregroundStyle(Theme.Colors.textPrimary)
            Spacer()
            Picker("Translate into", selection: $store.targetLanguage) {
                ForEach(TargetLanguage.allCases) { language in
                    Text(language.title).tag(language)
                }
            }
            .labelsHidden()
            .pickerStyle(.menu)
            .fixedSize()
            IconButton(systemImage: "gearshape", label: "Settings") { focus.isSettingsOpen = true }
        }
    }

    /// One line that grows to four. Return explains; Shift-Return adds a new line.
    private var inputBox: some View {
        @Bindable var store = store

        return VStack(alignment: .trailing, spacing: Theme.Spacing.small) {
            HStack(alignment: .center, spacing: Theme.Spacing.small) {
                TextField("A word or a sentence", text: $store.inputText, axis: .vertical)
                    .textFieldStyle(.plain)
                    .font(.body)
                    .lineLimit(1...4)
                    .focused($isInputFocused)
                    .onSubmit(explain)
                    .padding(.vertical, Theme.Spacing.small)
                IconButton(systemImage: "arrow.up", label: "Explain", isProminent: true, action: explain)
                    .disabled(!store.canTranslate)
                    .opacity(store.canTranslate ? 1 : 0.4)
            }
            .padding(.leading, Theme.Spacing.medium)
            .padding(.trailing, Theme.Spacing.xSmall)
            .padding(.vertical, Theme.Spacing.xSmall)
            .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.block)
                    .strokeBorder(isInputFocused ? Theme.Colors.accent : Theme.Colors.hairline,
                                  lineWidth: isInputFocused ? 2 : 1)
            }
            if store.isOverLimit {
                CharacterCounter(count: store.inputLength, limit: TranslationStore.maxInputLength)
            }
        }
    }

    private func explain() {
        guard store.canTranslate else { return }
        Task { await store.translate() }
    }
}

#Preview {
    MenuBarContentView()
        .environment(TranslationStore())
        .environment(SpeechPlayer.silent())
        .environment(AccountStore(secrets: NoSecretStore()))
        .environment(LikedStore.live())
        .environment(MenuBarFocus())
}
