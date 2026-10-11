//
//  WordNormalizer.swift
//  EnglishTranslation
//

import Foundation

/// Cleans a typed word the same way as the backend (`CacheKeys.normalizeWord`),
/// so the phone and the server use the same key.
enum WordNormalizer {
    /// Trim, collapse inner spaces to one, lowercase. "  Run  " becomes "run".
    static func normalize(_ word: String) -> String {
        let trimmed = word.trimmingCharacters(in: .whitespacesAndNewlines)
        let collapsed = trimmed.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        return collapsed.lowercased()
    }
}
