//
//  TranslationExtension.swift
//  TranslationExtension
//

import ExtensionKit
import SwiftUI
import TranslationUIProvider

/// The entry point of the translation extension (iOS 18.4 and later).
///
/// When Saynata is the default translation app (Settings, Apps, Default Apps, Translation), the system
/// "Translate" item on selected text in any app opens this extension. Apple's guide:
/// https://developer.apple.com/documentation/translationuiprovider/preparing-your-app-to-be-the-default-translation-app
@main
class TranslationExtension: TranslationUIProviderExtension {
    required init() {}

    var body: some TranslationUIProviderExtensionScene {
        TranslationUIProviderSelectedTextScene { context in
            TranslationSheetView(context: context)
        }
    }
}

/// Shows the same sheet as the share extension for the text the user selected.
/// There is no "Replace with translation" button: Saynata explains the text, it does not replace it.
private struct TranslationSheetView: View {
    let context: TranslationUIProviderContext

    var body: some View {
        SelectedTextResultView(
            loadText: { context.inputText.map { String($0.characters) } ?? "" },
            onClose: { context.finish(translation: nil) })
    }
}
