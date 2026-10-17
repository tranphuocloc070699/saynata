//
//  ThemeSnippetColorsTests.swift
//  EnglishTranslationTests
//

import Testing
import UIKit
@testable import EnglishTranslation

/// The Shortcuts card uses numbers instead of the asset catalog (TASK-87). They must stay equal to the catalog.
struct ThemeSnippetColorsTests {
    @Test(arguments: Array(Theme.SnippetColors.rgb.keys).sorted())
    func snippetColorMatchesTheAssetCatalog(name: String) throws {
        let asset = try #require(UIColor(named: "Theme/\(name)", in: .main, compatibleWith: nil))
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        #expect(asset.getRed(&red, green: &green, blue: &blue, alpha: &alpha))
        let rgb = (UInt32((red * 255).rounded()) << 16) | (UInt32((green * 255).rounded()) << 8) | UInt32((blue * 255).rounded())
        #expect(rgb == Theme.SnippetColors.rgb[name], "Theme/\(name) changed: update Theme.SnippetColors")
    }
}
