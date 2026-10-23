//
//  CatSpriteEngine.swift
//  EnglishTranslation
//

import CoreGraphics
import Foundation
import ImageIO
import QuartzCore
#if os(iOS)
import UIKit
#else
import AppKit
#endif

/// A sprite sheet of the paid cat pack (TASK-96): one horizontal row of equal frames.
/// The pictures are NOT in git (public repo, no license to redistribute). `scripts/install-cat-assets.sh` copies them
/// into `EnglishTranslation/PaidAssets/Cats/`, and a build phase puts them in the app bundle when they are there.
struct CatSpriteSheet: Equatable, Sendable {
    /// File name in the bundle, without ".png".
    let resource: String
    let frameCount: Int
    /// One frame, in pixels of the source picture.
    let frameSize: CGSize
    /// SF Symbol shown when the picture is not in the bundle. `nil` means show nothing.
    let fallbackSymbol: String?

    /// Cat sleeping in its bed: 4 frames of 32x32.
    static let sleep = CatSpriteSheet(resource: "cat-sleep", frameCount: 4, frameSize: CGSize(width: 32, height: 32),
                                      fallbackSymbol: "cat.fill")
    /// Cat sitting and moving a little: 10 frames of 32x32 (used by the Settings profile card).
    static let idle = CatSpriteSheet(resource: "cat-idle", frameCount: 10, frameSize: CGSize(width: 32, height: 32),
                                     fallbackSymbol: "cat.fill")
    /// A blue ball bouncing: 5 frames of 24x16.
    static let ball = CatSpriteSheet(resource: "cat-ball", frameCount: 5, frameSize: CGSize(width: 24, height: 16),
                                     fallbackSymbol: nil)

    /// The part of the picture to show for one frame, in the unit coordinates that `CALayer.contentsRect` uses
    /// (0 to 1, the origin is the bottom left of the picture).
    /// The frames sit in one row at the top of the picture; this is the formula from the pack's README, so a taller
    /// picture would still work.
    func contentsRect(frame: Int, imageSize: CGSize) -> CGRect {
        let index = min(max(frame, 0), max(frameCount - 1, 0))
        let sourceX = CGFloat(index) * frameSize.width
        let sourceY: CGFloat = 0
        return CGRect(
            x: sourceX / imageSize.width,
            y: 1 - (sourceY + frameSize.height) / imageSize.height,
            width: frameSize.width / imageSize.width,
            height: frameSize.height / imageSize.height)
    }

    /// One rect per frame, in order.
    func allContentsRects(imageSize: CGSize) -> [CGRect] {
        (0..<frameCount).map { contentsRect(frame: $0, imageSize: imageSize) }
    }

    /// Key times for a discrete keyframe animation: one more than the values, from 0 to 1 (Apple's convention).
    var discreteKeyTimes: [NSNumber] {
        (0...frameCount).map { NSNumber(value: Double($0) / Double(frameCount)) }
    }

    /// The time one full loop takes at `fps` frames a second.
    func loopDuration(fps: Double) -> CFTimeInterval {
        CFTimeInterval(frameCount) / max(fps, 1)
    }
}

/// Loads each picture once and keeps the decoded `CGImage`, shared by every cat on screen.
enum CatSpriteImageCache {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var images: [String: CGImage] = [:]

    /// The decoded picture, or `nil` when the file is not in the bundle (the paid pack is not installed).
    static func image(named resource: String, bundle: Bundle = .main) -> CGImage? {
        let key = bundle.bundlePath + "/" + resource
        lock.lock()
        defer { lock.unlock() }
        if let cached = images[key] { return cached }
        guard let url = bundle.url(forResource: resource, withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        else { return nil }
        // Decode now, not on the first draw, so the animation never stalls.
        let options = [kCGImageSourceShouldCacheImmediately: true] as CFDictionary
        guard let image = CGImageSourceCreateImageAtIndex(source, 0, options) else { return nil }
        images[key] = image
        return image
    }

    /// `true` when the sheet's picture can be loaded.
    static func isAvailable(_ resource: String, bundle: Bundle = .main) -> Bool {
        image(named: resource, bundle: bundle) != nil
    }
}

/// A back-and-forth move on the floor (the ball): the sprite goes from the left end to the right end and back.
struct CatSpriteRoll: Equatable, Sendable {
    /// How far it moves, in points. The view is this much wider than the sprite.
    var distance: CGFloat
    /// Seconds for one way.
    var duration: CFTimeInterval = 3
}

/// Owns the `CALayer` of one cat and its animations. Shared by the iOS and the macOS view.
/// Nothing here touches SwiftUI: frames change inside Core Animation, so there is no redraw per frame.
@MainActor
final class CatSpritePlayer {
    let layer = CALayer()
    private(set) var isRunning = false

    private let sheet: CatSpriteSheet
    private let image: CGImage
    private let fps: Double
    private let roll: CatSpriteRoll?
    private let spriteSize: CGSize
    private var bounds: CGRect = .zero

    init(sheet: CatSpriteSheet, image: CGImage, fps: Double, spriteSize: CGSize, roll: CatSpriteRoll?) {
        self.sheet = sheet
        self.image = image
        self.fps = fps
        self.roll = roll
        self.spriteSize = spriteSize
        layer.contents = image
        layer.magnificationFilter = .nearest
        layer.minificationFilter = .nearest
        layer.contentsGravity = .resize
        layer.isOpaque = false
        showStillFrame()
    }

    /// Called when the view's size changes. Puts the sprite in the view and starts the animation again.
    func layout(in bounds: CGRect) {
        self.bounds = bounds
        withoutImplicitAnimations {
            layer.bounds = CGRect(origin: .zero, size: spriteSize)
            // The ball starts on the left end; a cat that does not roll sits in the middle.
            let x = roll == nil ? bounds.midX : spriteSize.width / 2
            layer.position = CGPoint(x: x, y: bounds.midY)
        }
        if isRunning { restartAnimations() }
    }

    /// `visible`: the view is in a window. `reduceMotion`: the user asked for no movement.
    func update(isVisible: Bool, reduceMotion: Bool) {
        let shouldRun = isVisible && !reduceMotion
        if shouldRun {
            isRunning = true
            restartAnimations()
        } else {
            isRunning = false
            layer.removeAllAnimations()
            showStillFrame()
        }
    }

    private func showStillFrame() {
        withoutImplicitAnimations {
            layer.contentsRect = sheet.contentsRect(frame: 0, imageSize: imageSize)
        }
    }

    private var imageSize: CGSize { CGSize(width: image.width, height: image.height) }

    private func restartAnimations() {
        layer.removeAllAnimations()
        // The frames: a discrete keyframe animation on contentsRect (no blending between neighbours).
        let frames = CAKeyframeAnimation(keyPath: "contentsRect")
        frames.values = sheet.allContentsRects(imageSize: imageSize).map(Self.value(for:))
        frames.keyTimes = sheet.discreteKeyTimes
        frames.calculationMode = .discrete
        frames.duration = sheet.loopDuration(fps: fps)
        frames.repeatCount = .infinity
        frames.isRemovedOnCompletion = false
        layer.add(frames, forKey: "frames")

        if let roll, bounds.width > spriteSize.width {
            let move = CABasicAnimation(keyPath: "position.x")
            move.fromValue = spriteSize.width / 2
            move.toValue = bounds.width - spriteSize.width / 2
            move.duration = roll.duration
            move.autoreverses = true
            move.repeatCount = .infinity
            move.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
            move.isRemovedOnCompletion = false
            layer.add(move, forKey: "roll")
        }
    }

    private func withoutImplicitAnimations(_ body: () -> Void) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        body()
        CATransaction.commit()
    }

    private static func value(for rect: CGRect) -> NSValue {
        #if os(iOS)
        NSValue(cgRect: rect)
        #else
        NSValue(rect: rect)
        #endif
    }
}
