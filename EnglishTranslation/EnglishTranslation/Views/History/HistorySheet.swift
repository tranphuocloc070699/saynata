//
//  HistorySheet.swift
//  EnglishTranslation
//

import SwiftUI

/// The sheet behind the clock button (TASK-93.2, mockup option A): a `History | Liked` switch, a search box, and
/// rows grouped by day. Tap a row to show that result again; swipe left to delete it.
/// History is on this device; Liked comes from the account (a guest sees how to start).
struct HistorySheet: View {
    enum Tab: String, CaseIterable, Identifiable {
        case history = "History"
        case liked = "Liked"
        var id: String { rawValue }
    }

    let history: any HistoryStoring
    /// Called with the text of the tapped row. The caller puts it in the input and runs the normal search.
    let onSelect: (String) -> Void

    @Environment(LikedStore.self) private var liked
    @Environment(AccountStore.self) private var account
    @Environment(\.dismiss) private var dismiss

    @State private var tab = Tab.history
    @State private var search = ""
    @State private var historyItems: [SavedItem] = []
    @State private var isAskingToClear = false

    var body: some View {
        VStack(spacing: Theme.Spacing.medium) {
            topBar
            searchField
            content
        }
        .padding(.top, Theme.Spacing.large)
        .background(Theme.Colors.background)
        .task {
            historyItems = await history.items()
            await liked.refresh()
        }
        .confirmationDialog("Clear all of History?", isPresented: $isAskingToClear, titleVisibility: .visible) {
            Button("Clear History", role: .destructive) {
                Task {
                    await history.removeAll()
                    historyItems = []
                }
            }
        }
    }

    // MARK: - Parts

    private var topBar: some View {
        HStack(spacing: Theme.Spacing.small) {
            Picker("List", selection: $tab) {
                ForEach(Tab.allCases) { tab in
                    Text(tab.rawValue).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            if tab == .history, !historyItems.isEmpty {
                IconButton(systemImage: "trash", label: "Clear History") { isAskingToClear = true }
            }
            IconButton(systemImage: "xmark", label: "Close") { dismiss() }
        }
        .padding(.horizontal, Theme.Spacing.large)
    }

    private var searchField: some View {
        HStack(spacing: Theme.Spacing.small) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Theme.Colors.textMuted)
            TextField("Search", text: $search)
                .autocorrectionDisabled()
            if !search.isEmpty {
                IconButton(systemImage: "xmark.circle.fill", label: "Clear search", style: .plain) { search = "" }
            }
        }
        .padding(.horizontal, Theme.Spacing.medium)
        .frame(minHeight: Theme.minTapTarget)
        .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.block).strokeBorder(Theme.Colors.hairline, lineWidth: 1)
        }
        .padding(.horizontal, Theme.Spacing.large)
    }

    private var currentItems: [SavedItem] {
        (tab == .history ? historyItems : liked.items).matching(search)
    }

    @ViewBuilder
    private var content: some View {
        let groups = DayGroup.groups(of: currentItems)
        if groups.isEmpty {
            emptyMessage
        } else {
            List {
                ForEach(groups) { group in
                    Section {
                        ForEach(group.items) { item in
                            row(item)
                                .listRowBackground(Theme.Colors.background)
                                .listRowSeparatorTint(Theme.Colors.hairline)
                        }
                    } header: {
                        Text(group.title)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Theme.Colors.textMuted)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private var emptyMessage: some View {
        VStack {
            Text(emptyText)
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(Theme.Spacing.xLarge)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyText: String {
        if !search.isEmpty { return "Nothing found." }
        switch tab {
        case .history: return "Words and sentences you look up will show here."
        case .liked:
            return account.isSignedIn
                ? "Tap the star on a word or sentence to keep it here."
                : "Sign in, then tap the star on a word or sentence to keep it here."
        }
    }

    private func row(_ item: SavedItem) -> some View {
        Button {
            dismiss()
            onSelect(item.text)
        } label: {
            SavedItemRow(item: item)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                delete(item)
            } label: {
                Image(systemName: "trash")
            }
            .accessibilityLabel("Delete")
        }
    }

    private func delete(_ item: SavedItem) {
        switch tab {
        case .history:
            historyItems.removeAll { $0.id == item.id }
            Task { await history.remove(id: item.id) }
        case .liked:
            Task { await liked.unlike(id: item.id) }
        }
    }
}

/// One row: a word with its label and short meaning, or a sentence (serif) with its simple summary.
struct SavedItemRow: View {
    let item: SavedItem

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
            switch item.kind {
            case .word:
                HStack(spacing: Theme.Spacing.small) {
                    Text(item.text)
                        .font(.headline)
                        .foregroundStyle(Theme.Colors.textPrimary)
                    if item.isPhrase {
                        LevelChip(text: "phrase")
                    } else if let chip = item.chip {
                        LevelChip(text: chip)
                    }
                }
            case .sentence:
                Text(item.text)
                    .font(.system(.body, design: .serif))
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .lineLimit(2)
            }
            if !item.preview.isEmpty {
                Text(item.preview)
                    .font(.subheadline)
                    .foregroundStyle(Theme.Colors.textSecondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, Theme.Spacing.xSmall)
        .contentShape(Rectangle())
    }
}

/// A history that lives in memory, for previews and tests.
actor MemoryHistory: HistoryStoring {
    private var stored: [SavedItem]

    init(_ items: [SavedItem] = []) {
        stored = items
    }

    func record(_ item: SavedItem) {
        stored.removeAll { $0.id == item.id }
        stored.insert(item, at: 0)
    }

    func items() -> [SavedItem] { stored }

    func remove(id: String) {
        stored.removeAll { $0.id == id }
    }

    func removeAll() {
        stored = []
    }
}

#Preview {
    let now = Date.now
    let items = [
        SavedItem(kind: .word, text: "depend", preview: "to rely on someone or something", chip: "verb · B1", date: now),
        SavedItem(kind: .word, text: "give up", preview: "to stop trying", date: now.addingTimeInterval(-3600)),
        SavedItem(kind: .sentence, text: "The mist blurred the edges of the world.",
                  preview: "The fog made everything hard to see.", date: now.addingTimeInterval(-90_000)),
    ]
    return HistorySheet(history: MemoryHistory(items), onSelect: { _ in })
        .environment(LikedStore(makeAPI: { throw BackendError.signInRequired }, copy: PreviewLikedCopy(), isSignedIn: { false }))
        .environment(AccountStore(secrets: NoSecretStore()))
}

private struct PreviewLikedCopy: LikedCopyStoring {
    func load() -> [SavedItem] { [] }
    func save(_ items: [SavedItem]) {}
}
