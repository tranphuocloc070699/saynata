//
//  CatSpriteView.swift
//  EnglishTranslation
//

import SwiftUI

/// An animated pixel-art sprite from the paid cat pack (TASK-96): the sleeping cat, the idle cat, the blue ball.
///
/// Cheap on purpose (the method is in `~/Downloads/CatFormSprites/README.md`): the sheet is decoded once and shared
/// (`CatSpriteImageCache`), every cat is one `CALayer`, and the frames change with a discrete `CAKeyframeAnimation`
/// on `contentsRect`. SwiftUI never redraws per frame. The animation is removed when the view leaves the window,
/// and with Reduce Motion the first frame stands still.
///
/// When the picture is not in the bundle (the pack is not installed), it shows `sheet.fallbackSymbol` instead
/// (or nothing, for the ball).
struct CatSpriteView: View {
    let sheet: CatSpriteSheet
    /// The size of the sprite in points. Use a whole multiple of the pixel size (for example 64 for 32 pixels)
    /// to keep the pixels square.
    var size: CGSize
    var fps: Double = 8
    /// Moves the sprite back and forth; the view gets this much wider.
    var roll: CatSpriteRoll?
    var bundle: Bundle = .main

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if let image = CatSpriteImageCache.image(named: sheet.resource, bundle: bundle) {
                CatSpriteRepresentable(sheet: sheet, image: image, fps: fps, size: size, roll: roll,
                                       reduceMotion: reduceMotion)
            } else if let symbol = sheet.fallbackSymbol {
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(Theme.Colors.textSecondary)
                    .frame(width: size.width, height: size.height)
            } else {
                Color.clear
            }
        }
        .frame(width: size.width + (roll?.distance ?? 0), height: size.height)
        // A decorative picture: no VoiceOver stop, and it never takes a tap from the screen under it.
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }
}

#if os(iOS)
import UIKit

private struct CatSpriteRepresentable: UIViewRepresentable {
    let sheet: CatSpriteSheet
    let image: CGImage
    let fps: Double
    let size: CGSize
    let roll: CatSpriteRoll?
    let reduceMotion: Bool

    func makeUIView(context: Context) -> CatSpriteHostView {
        CatSpriteHostView(player: CatSpritePlayer(sheet: sheet, image: image, fps: fps, spriteSize: size, roll: roll))
    }

    func updateUIView(_ view: CatSpriteHostView, context: Context) {
        view.reduceMotion = reduceMotion
    }
}

/// A plain `UIView` that hosts the sprite layer and tells the player when it is on screen.
final class CatSpriteHostView: UIView {
    private let player: CatSpritePlayer
    var reduceMotion = false { didSet { refresh() } }

    init(player: CatSpritePlayer) {
        self.player = player
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        backgroundColor = .clear
        player.layer.contentsScale = UIScreen.main.scale
        layer.addSublayer(player.layer)
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func layoutSubviews() {
        super.layoutSubviews()
        player.layout(in: bounds)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        refresh()
    }

    private func refresh() {
        player.update(isVisible: window != nil, reduceMotion: reduceMotion)
    }
}
#else
import AppKit

private struct CatSpriteRepresentable: NSViewRepresentable {
    let sheet: CatSpriteSheet
    let image: CGImage
    let fps: Double
    let size: CGSize
    let roll: CatSpriteRoll?
    let reduceMotion: Bool

    func makeNSView(context: Context) -> CatSpriteHostView {
        CatSpriteHostView(player: CatSpritePlayer(sheet: sheet, image: image, fps: fps, spriteSize: size, roll: roll))
    }

    func updateNSView(_ view: CatSpriteHostView, context: Context) {
        view.reduceMotion = reduceMotion
    }
}

/// A plain `NSView` that hosts the sprite layer and tells the player when it is on screen.
final class CatSpriteHostView: NSView {
    private let player: CatSpritePlayer
    var reduceMotion = false { didSet { refresh() } }

    init(player: CatSpritePlayer) {
        self.player = player
        super.init(frame: .zero)
        wantsLayer = true
        layer?.addSublayer(player.layer)
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    // Decorative: a click goes to the view under it.
    override func hitTest(_ point: NSPoint) -> NSView? { nil }

    override func layout() {
        super.layout()
        player.layer.contentsScale = window?.backingScaleFactor ?? 2
        player.layout(in: bounds)
    }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        refresh()
    }

    private func refresh() {
        player.update(isVisible: window != nil, reduceMotion: reduceMotion)
    }
}
#endif

#Preview("Sprites (needs the cat pack, else the fallback)") {
    VStack(spacing: Theme.Spacing.large) {
        CatSpriteView(sheet: .sleep, size: CGSize(width: 64, height: 64), fps: 5)
        CatSpriteView(sheet: .idle, size: CGSize(width: 64, height: 64), fps: 8)
        CatSpriteView(sheet: .ball, size: CGSize(width: 48, height: 32), fps: 6,
                      roll: CatSpriteRoll(distance: 120))
    }
    .padding()
    .background(Theme.Colors.roomSky)
}
