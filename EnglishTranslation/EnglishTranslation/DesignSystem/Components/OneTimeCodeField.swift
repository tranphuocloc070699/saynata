//
//  OneTimeCodeField.swift
//  EnglishTranslation
//

import SwiftUI
import UIKit

/// The text field under `CodeBoxes`: a plain UIKit `UITextField` with `.oneTimeCode`.
///
/// Why UIKit: with a SwiftUI `TextField`, tapping the code that iOS shows above the keyboard ("From Mail") did not
/// reach the field on the developer's iPhone, so the code was never checked. A `UITextField` gets the whole code in
/// one change, which is the case iOS AutoFill is built for.
/// See https://developer.apple.com/documentation/security/enabling-password-autofill-on-a-text-input-view
struct OneTimeCodeField: UIViewRepresentable {
    @Binding var text: String
    var maxLength: Int
    /// Opens the number pad once, when the field first appears.
    var focusOnAppear = true
    /// Tells the boxes when the field starts or stops editing (to draw the accent border).
    var onEditingChanged: (Bool) -> Void = { _ in }

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.keyboardType = .numberPad
        field.textContentType = .oneTimeCode
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        // The boxes draw the digits, so the field's own text and caret are hidden.
        field.textColor = .clear
        field.tintColor = .clear
        field.backgroundColor = .clear
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.textChanged(_:)), for: .editingChanged)
        field.accessibilityLabel = "Code"
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        if focusOnAppear {
            // After the field is in a window, or becomeFirstResponder does nothing.
            DispatchQueue.main.async { field.becomeFirstResponder() }
        }
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        if field.text != text {
            field.text = text
        }
        // Focus is NOT driven from SwiftUI here. An earlier version mirrored a SwiftUI @FocusState, which SwiftUI resets to
        // false for a UIKit field; that closed the keyboard right after it opened (a white flash, no number pad).
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: OneTimeCodeField

        init(parent: OneTimeCodeField) {
            self.parent = parent
        }

        /// Typing one digit or AutoFill pasting all six: keep digits only, at most `maxLength`.
        @objc func textChanged(_ field: UITextField) {
            let digits = String((field.text ?? "").filter(\.isNumber).prefix(parent.maxLength))
            if field.text != digits {
                field.text = digits
            }
            parent.text = digits
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.onEditingChanged(true)
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.onEditingChanged(false)
        }
    }
}
