//
//  TitledSection.swift
//  EnglishTranslation
//

import SwiftUI

/// A bold heading with content below it. Shared by any screen.
/// Not named `Section`, so it doesn't clash with SwiftUI's own `Section`.
struct TitledSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.headline)
            content
        }
    }
}
