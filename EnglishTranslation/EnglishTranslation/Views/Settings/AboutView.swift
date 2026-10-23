//
//  AboutView.swift
//  EnglishTranslation
//

import SwiftUI

/// The "About Saynata" page (TASK-98): the app name, its version and one line about it.
struct AboutView: View {
    /// "1.0 (3)", read from Info.plist. Shows "1.0" when the build number is missing.
    static func versionText(info: [String: Any]? = Bundle.main.infoDictionary) -> String {
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        guard let build = info?["CFBundleVersion"] as? String, !build.isEmpty else { return version }
        return "\(version) (\(build))"
    }

    var body: some View {
        VStack(spacing: Theme.Spacing.medium) {
            CatSpriteView(sheet: .idle, size: CGSize(width: 96, height: 96))
            Text("Saynata")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Theme.Colors.textPrimary)
            Text("Version \(Self.versionText())")
                .font(.footnote)
                .foregroundStyle(Theme.Colors.textMuted)
            Text("Saynata helps you learn English. It explains words and sentences in simple English first.")
                .font(.body)
                .foregroundStyle(Theme.Colors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(Theme.Spacing.xLarge)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Colors.background)
        .navigationTitle("About Saynata")
        .inlineNavigationTitle()
    }
}

#Preview {
    NavigationStack { AboutView() }
}
