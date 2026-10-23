//
//  CatSpriteTests.swift
//  EnglishTranslationTests
//

import CoreGraphics
import Foundation
import Testing
@testable import EnglishTranslation

/// The paid cat pictures are not in git, so these tests use only numbers and an empty bundle.
struct CatSpriteTests {
    private let sleepImage = CGSize(width: 128, height: 32)
    private let ballImage = CGSize(width: 120, height: 16)

    @Test func firstFrameStartsAtTheLeft() {
        let rect = CatSpriteSheet.sleep.contentsRect(frame: 0, imageSize: sleepImage)
        #expect(rect == CGRect(x: 0, y: 0, width: 0.25, height: 1))
    }

    @Test func framesMoveRightByOneFrameWidth() {
        let rect = CatSpriteSheet.sleep.contentsRect(frame: 3, imageSize: sleepImage)
        #expect(rect.minX == 0.75)
        #expect(rect.width == 0.25)
    }

    @Test func ballFramesAre24By16OfA120By16Sheet() {
        let rect = CatSpriteSheet.ball.contentsRect(frame: 2, imageSize: ballImage)
        #expect(abs(rect.minX - 48.0 / 120.0) < 0.0001)
        #expect(abs(rect.width - 24.0 / 120.0) < 0.0001)
        #expect(rect.minY == 0)
        #expect(rect.height == 1)
    }

    @Test func aFrameOutsideTheSheetIsClamped() {
        let sheet = CatSpriteSheet.sleep
        #expect(sheet.contentsRect(frame: 99, imageSize: sleepImage) == sheet.contentsRect(frame: 3, imageSize: sleepImage))
        #expect(sheet.contentsRect(frame: -1, imageSize: sleepImage) == sheet.contentsRect(frame: 0, imageSize: sleepImage))
    }

    @Test func oneRectPerFrameAndTheyDoNotOverlap() {
        let rects = CatSpriteSheet.idle.allContentsRects(imageSize: CGSize(width: 320, height: 32))
        #expect(rects.count == 10)
        for (a, b) in zip(rects, rects.dropFirst()) {
            #expect(abs(a.maxX - b.minX) < 0.0001)
        }
    }

    @Test func discreteKeyTimesHaveOneMoreThanTheFrames() {
        let times = CatSpriteSheet.sleep.discreteKeyTimes.map(\.doubleValue)
        #expect(times == [0, 0.25, 0.5, 0.75, 1])
    }

    @Test func loopDurationFollowsTheFrameRate() {
        #expect(CatSpriteSheet.sleep.loopDuration(fps: 4) == 1)
        #expect(CatSpriteSheet.idle.loopDuration(fps: 10) == 1)
    }

    // MARK: - Missing pictures

    /// A folder with no pictures in it, used as a bundle.
    private func emptyBundle() throws -> Bundle {
        let url = FileManager.default.temporaryDirectory.appending(path: "no cats \(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return try #require(Bundle(url: url))
    }

    @Test func missingPictureGivesNoImage() throws {
        let bundle = try emptyBundle()
        #expect(CatSpriteImageCache.image(named: "cat-sleep", bundle: bundle) == nil)
        #expect(!CatSpriteImageCache.isAvailable("cat-room", bundle: bundle))
    }

    @Test func aPictureInTheBundleIsDecodedOnceAndShared() throws {
        let folder = FileManager.default.temporaryDirectory.appending(path: "cats \(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        // A 1x1 PNG made here, so no paid file is needed.
        try Self.tinyPNG.write(to: folder.appending(path: "cat-test.png"))
        let bundle = try #require(Bundle(url: folder))
        let first = try #require(CatSpriteImageCache.image(named: "cat-test", bundle: bundle))
        let second = try #require(CatSpriteImageCache.image(named: "cat-test", bundle: bundle))
        #expect(first === second)
        #expect(first.width == 1)
    }

    @Test func fallbackSymbolIsACatExceptForTheBall() {
        #expect(CatSpriteSheet.sleep.fallbackSymbol == "cat.fill")
        #expect(CatSpriteSheet.idle.fallbackSymbol == "cat.fill")
        #expect(CatSpriteSheet.ball.fallbackSymbol == nil)
    }

    /// The smallest valid PNG: one transparent pixel.
    private static let tinyPNG = Data(base64Encoded:
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")!
}
