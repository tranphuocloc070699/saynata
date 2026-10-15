//
//  CodeBoxes.swift
//  EnglishTranslation
//

import SwiftUI

/// Six boxes for a one-time code (TASK-70). It is ONE text field, drawn as boxes: the field sits on top of them with
/// clear text, so a tap anywhere opens the number pad. The field is `OneTimeCodeField` (UIKit, `.oneTimeCode`), so
/// the code from Mail fills it.
/// The box that comes next gets the accent border (focus is one of the places the accent may be used).
struct CodeBoxes: View {
    @Binding var code: String
    var length = 6
    /// Opens the number pad when the boxes appear.
    var focusOnAppear = true

    /// True while the code field is editing. Plain state, not @FocusState: the field is UIKit (see OneTimeCodeField).
    @State private var isEditing = false

    var body: some View {
        ZStack {
            HStack(spacing: Theme.Spacing.small) {
                ForEach(0..<length, id: \.self) { index in
                    box(at: index)
                }
            }
            // A UIKit field, so the code iOS shows above the keyboard (AutoFill "From Mail") really fills it.
            OneTimeCodeField(
                text: $code,
                maxLength: length,
                focusOnAppear: focusOnAppear,
                onEditingChanged: { isEditing = $0 })
                .frame(maxWidth: .infinity, minHeight: 56)
                .accessibilityValue(code.isEmpty ? "Empty" : code)
        }
    }

    private func box(at index: Int) -> some View {
        let digits = Array(code)
        let isNext = isEditing && index == min(digits.count, length - 1)
        return Text(index < digits.count ? String(digits[index]) : "")
            .font(.title2.monospacedDigit().weight(.semibold))
            .foregroundStyle(Theme.Colors.textPrimary)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.block)
                    .strokeBorder(isNext ? Theme.Colors.accent : Theme.Colors.hairline, lineWidth: isNext ? 2 : 1)
            }
            .accessibilityHidden(true)
    }
}

#Preview {
    struct Demo: View {
        @State private var code = "482"
        var body: some View {
            CodeBoxes(code: $code)
                .padding()
                .background(Theme.Colors.background)
        }
    }
    return Demo()
}
