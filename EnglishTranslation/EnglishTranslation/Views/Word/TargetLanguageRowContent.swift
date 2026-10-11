//
//  TargetLanguageRowContent.swift
//  EnglishTranslation
//

import SwiftUI
import Translation

/// The inside of the last folded row on a meaning card (TASK-40).
/// Vietnamese shows the dictionary text. Other languages translate the simple English
/// explanation on the device with Apple's Translation framework.
/// This view only exists while the row is open, so nothing is translated before the user asks.
struct TargetLanguageRowContent: View {
    let sense: Sense
    let language: TargetLanguage

    private enum Phase: Equatable {
        case translating
        case done(String)
        case failed(String)
    }

    @State private var phase: Phase = .translating
    /// Setting this starts the translation. iOS shows the download sheet if the language pack is missing.
    @State private var configuration: TranslationSession.Configuration?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            if let text = sense.dictionaryText(for: language) {
                bodyText(text)
            } else {
                switch phase {
                case .translating:
                    ProgressView().controlSize(.small)
                case let .done(text):
                    bodyText(text)
                    Text("Translated on this iPhone")
                        .font(.caption)
                        .foregroundStyle(Theme.Colors.textMuted)
                case let .failed(message):
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(Theme.Colors.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task(id: language) { await prepare() }
        .translationTask(configuration) { session in
            await translate(with: session)
        }
    }

    private func bodyText(_ text: String) -> some View {
        Text(text)
            .font(.body)
            .foregroundStyle(Theme.Colors.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Uses the cache if it can. Otherwise checks the language pair, then starts the translation.
    private func prepare() async {
        guard sense.dictionaryText(for: language) == nil else { return }
        let english = sense.simpleDefinition
        if let cached = TranslationCache.shared.translation(of: english, in: language) {
            phase = .done(cached)
            return
        }
        phase = .translating
        let source = Locale.Language(identifier: "en")
        let target = Locale.Language(identifier: language.appleIdentifier)
        let status = await LanguageAvailability().status(from: source, to: target)
        if status == .unsupported {
            phase = .failed("\(language.title) is not available for on-device translation.")
            return
        }
        configuration = .init(source: source, target: target)
    }

    private func translate(with session: TranslationSession) async {
        let english = sense.simpleDefinition
        let chosen = language
        do {
            let response = try await session.translate(english)
            TranslationCache.shared.save(response.targetText, of: english, in: chosen)
            phase = .done(response.targetText)
        } catch {
            phase = .failed("Could not translate. The \(chosen.title) language pack may be missing.")
        }
    }
}
