//
//  PlatformSupport.swift
//  EnglishTranslation
//
//  The few things that differ between iOS (UIKit) and the Mac app (AppKit), in one place (TASK-85.1).
//  Shared code calls these names; only this file says `#if os(...)`.
//

import SwiftUI

#if os(iOS)
import UIKit

/// A decoded picture: `UIImage` on iOS, `NSImage` on the Mac.
typealias PlatformImage = UIImage

extension Image {
    init(platformImage: PlatformImage) {
        self.init(uiImage: platformImage)
    }
}

enum Pasteboard {
    /// Puts plain text on the clipboard.
    static func copy(_ text: String) {
        UIPasteboard.general.string = text
    }
}

/// Apple's spelling guesses for one English word.
enum SystemSpellChecker {
    static func guesses(for word: String) -> [String] {
        // A new checker for each call: it is cheap, and it is not safe to share between threads.
        let checker = UITextChecker()
        let range = NSRange(location: 0, length: (word as NSString).length)
        return checker.guesses(forWordRange: range, in: word, language: "en_US") ?? []
    }
}

#elseif os(macOS)
import AppKit

typealias PlatformImage = NSImage

extension Image {
    init(platformImage: PlatformImage) {
        self.init(nsImage: platformImage)
    }
}

enum Pasteboard {
    static func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
    }
}

enum SystemSpellChecker {
    static func guesses(for word: String) -> [String] {
        // The Mac has one shared checker. It must be used on the main thread, so hop there when needed.
        let range = NSRange(location: 0, length: (word as NSString).length)
        let ask = {
            NSSpellChecker.shared.guesses(
                forWordRange: range, in: word, language: "en_US", inSpellDocumentWithTag: 0) ?? []
        }
        if Thread.isMainThread { return MainActor.assumeIsolated(ask) }
        return DispatchQueue.main.sync { MainActor.assumeIsolated(ask) }
    }
}
#endif

extension View {
    /// An email field: the email keyboard, AutoFill, and no capital first letter on iOS. Nothing to set on the Mac.
    func emailFieldStyle() -> some View {
        #if os(iOS)
        self.keyboardType(.emailAddress)
            .textContentType(.emailAddress)
            .textInputAutocapitalization(.never)
        #else
        self
        #endif
    }

    /// A small title in the navigation bar on iOS. The Mac has no navigation bar title modes.
    func inlineNavigationTitle() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}

extension ToolbarItemPlacement {
    /// The top right of a sheet: `.topBarTrailing` on iOS, the confirm place on the Mac.
    static var sheetTrailing: ToolbarItemPlacement {
        #if os(iOS)
        .topBarTrailing
        #else
        .confirmationAction
        #endif
    }
}
