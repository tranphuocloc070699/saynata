//
//  CodeBoxes.swift
//  EnglishTranslation
//

import SwiftUI

/// Six boxes for a one-time code (TASK-70). It is ONE text field, drawn as boxes: the field sits on top of them with
/// clear text, so a tap anywhere opens the number pad and `.textContentType(.oneTimeCode)` lets Mail fill the code.
/// The box that comes next gets the accent border (focus is one of the places the accent may be used).
struct CodeBoxes: View {
    @Binding var code: String
    var length = 6
    var isFocused: FocusState<Bool>.Binding

    var body: some View {
        ZStack {
            HStack(spacing: Theme.Spacing.small) {
                ForEach(0..<length, id: \.self) { index in
                    box(at: index)
                }
            }
            TextField("", text: $code)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused(isFocused)
                .autocorrectionDisabled()
                // Hidden text and caret: the boxes draw the digits.
                .foregroundStyle(.clear)
                .tint(.clear)
                .accessibilityLabel("Code")
                .accessibilityValue(code.isEmpty ? "Empty" : code)
        }
    }

    private func box(at index: Int) -> some View {
        let digits = Array(code)
        let isNext = isFocused.wrappedValue && index == min(digits.count, length - 1)
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
        @FocusState private var focused: Bool
        var body: some View {
            CodeBoxes(code: $code, isFocused: $focused)
                .padding()
                .background(Theme.Colors.background)
                .onAppear { focused = true }
        }
    }
    return Demo()
}
