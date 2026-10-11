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

/// A group of usage items under one heading in the Grammar row.
struct UsageGroup: Equatable {
    let title: String
    let items: [Usage]
}

extension Sense {
    /// Usage items grouped by kind, in a fixed order: Patterns, Collocations, Phrases and idioms.
    /// A phrase and an idiom share one group. Empty groups are left out.
    var usageGroups: [UsageGroup] {
        let groups = [
            UsageGroup(title: "Patterns", items: usage.filter { $0.kind == .pattern }),
            UsageGroup(title: "Collocations", items: usage.filter { $0.kind == .collocation }),
            UsageGroup(title: "Phrases and idioms", items: usage.filter { $0.kind == .phrase || $0.kind == .idiom }),
        ]
        return groups.filter { !$0.items.isEmpty }
    }
}

extension WordEntry {
    /// Text for the pill under the headword: "past tense of run /rʌn/".
    /// Nil when the user typed the base form (no pill).
    var formPillText: String? {
        guard let form = matchedForm else { return nil }
        let text = "\(form.type) of \(word)"
        guard let ipa, !ipa.isEmpty else { return text }
        return "\(text) \(ipa)"
    }
}

extension Example.Level {
    /// The small label before an example.
    var label: String { rawValue }
}
