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

    private var mode: TranslationRowMode { TranslationRowMode(rawValue: modeRaw) ?? .collapsed }

    /// Uses the language name when there is a row to open. English has no target row.
    private var footerText: String {
        let name = targetLanguage.hasTargetLanguageRow ? targetLanguage.title : "translation"
        return "How the \(name) row opens on words and sentences."
    }

    var body: some View {
        NavigationStack {
            List {
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
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
        }
    }
}

#Preview {
    SettingsView()
}
