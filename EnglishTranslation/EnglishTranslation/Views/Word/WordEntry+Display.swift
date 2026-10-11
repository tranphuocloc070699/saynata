//
//  WordEntry+Display.swift
//  EnglishTranslation
//

import Foundation

/// Text helpers for the word card. The model (in `Dictionary/`) stays plain data.
extension Sense {
    /// Text for the level chip: "verb · A2", or just "verb" when the level is missing (empty).
    var chipText: String {
        cefr.isEmpty ? pos : "\(pos) · \(cefr)"
    }
}

extension Usage.Kind {
    /// Small label shown before the pattern. A plain pattern has no label.
    var label: String? {
        self == .pattern ? nil : rawValue
    }
}
