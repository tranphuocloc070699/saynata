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
    @Binding var isFocused: Bool
    var maxLength: Int

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
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        if field.text != text {
            field.text = text
        }
        // Focus is changed after this update, so UIKit is not asked to change the first responder mid-layout.
        if isFocused, !field.isFirstResponder {
            DispatchQueue.main.async { field.becomeFirstResponder() }
        } else if !isFocused, field.isFirstResponder {
            DispatchQueue.main.async { field.resignFirstResponder() }
        }
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
            if !parent.isFocused { parent.isFocused = true }
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            if parent.isFocused { parent.isFocused = false }
        }
    }
}
