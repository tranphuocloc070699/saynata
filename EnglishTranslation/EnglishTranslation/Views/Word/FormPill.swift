//
//  FormPill.swift
//  EnglishTranslation
//

import SwiftUI

/// The tinted pill under the headword when the user typed a form: "past tense of run /rʌn/".
struct FormPill: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "arrow.turn.down.right")
            .font(.subheadline)
            .foregroundStyle(Theme.Colors.textSecondary)
            .padding(.horizontal, Theme.Spacing.medium)
            .padding(.vertical, Theme.Spacing.small)
            .background(Theme.Colors.chipFill, in: Capsule())
    }
}

#Preview {
    FormPill(text: WordEntry.ran.formPillText ?? "")
        .padding()
}
