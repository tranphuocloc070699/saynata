//
//  TargetLanguageRowContent.swift
//  EnglishTranslation
//

import SwiftUI
import Translation

/// The inside of the last folded row on a meaning card (TASK-40, TASK-106.3).
/// The line comes from the backend (`POST /api/translate-lines`), asked once for every sense of the card.
/// When the backend has no line or the call fails: Vietnamese shows the dictionary text, and other languages
/// translate the simple English explanation on the device with Apple's Translation framework.
/// This view only exists while the row is open, so nothing is asked before the user opens it.
struct TargetLanguageRowContent: View {
    let sense: Sense
    let language: TargetLanguage
    /// The simple explanation of every sense of the card. One request asks for all of them.
    var cardTexts: [String] = []
    var lines: LanguageLines = .shared

    private enum Phase: Equatable {
        case translating
        case done(String)
        /// Translated by Apple on this device.
        case doneOnDevice(String)
        case failed(String)
    }

    @State private var phase: Phase = .translating
    /// Setting this starts the translation. iOS shows the download sheet if the language pack is missing.
    @State private var configuration: TranslationSession.Configuration?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            switch phase {
            case .translating:
                ProgressView().controlSize(.small)
            case let .done(text):
                bodyText(text)
            case let .doneOnDevice(text):
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

    /// The backend line first. If it is missing: the dictionary text (Vietnamese), else Apple Translation.
    private func prepare() async {
        let english = sense.simpleDefinition
        if let line = lines.line(for: english, in: language) {
            phase = .done(line)
            return
        }
        phase = .translating
        await lines.load(cardTexts.isEmpty ? [english] : cardTexts, in: language)
        if let line = lines.line(for: english, in: language) {
            phase = .done(line)
            return
        }
        if let text = sense.dictionaryText(for: language) {
            phase = .done(text)
            return
        }
        await prepareOnDevice(english)
    }

    /// Uses the Apple Translation cache if it can. Otherwise checks the language pair, then starts the translation.
    private func prepareOnDevice(_ english: String) async {
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
            phase = .doneOnDevice(response.targetText)
        } catch {
            phase = .failed("Could not translate. The \(chosen.title) language pack may be missing.")
        }
    }
}
