//
//  SignInCatRoom.swift
//  EnglishTranslation
//

import SwiftUI

/// The picture on top of the sign-in sheet (TASK-96): a pixel-art room, the cat asleep in its bed and a blue ball
/// that rolls slowly on the floor. The room is drawn at one fixed size and centred, so the cat stays on the bed
/// at any sheet width; the sky colour of the room fills the sides.
/// Without the paid pack in the bundle, it shows a still cat symbol on the same soft blue-sage background.
struct SignInCatRoom: View {
    var bundle: Bundle = .main

    /// The height of the picture.
    static let height: CGFloat = 300
    static let roomResource = "cat-room"

    /// The room picture is 512 pixels wide; it is shown at 312 pt (the size in the 2026-10-23 mockup).
    private let roomSize: CGFloat = 312
    /// How far the room is moved up, so the floor, not the sky, fills the bottom of the picture.
    private let roomTop: CGFloat = -10

    var body: some View {
        ZStack {
            Theme.Colors.roomSky
            if let room = CatSpriteImageCache.image(named: Self.roomResource, bundle: bundle) {
                scene(room: room)
            } else {
                Image(systemName: "cat.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(Theme.Colors.textSecondary)
                    .padding(.bottom, Theme.Spacing.large)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: Self.height)
        .clipped()
        .accessibilityHidden(true)
    }

    private func scene(room: CGImage) -> some View {
        // Everything is placed in the room's own coordinates, then the whole room is centred.
        ZStack(alignment: .topLeading) {
            Image(decorative: room, scale: 1)
                .resizable()
                .interpolation(.none)
                .frame(width: roomSize, height: roomSize)

            // The cat lies on the bed, "z z" floats above it.
            CatSpriteView(sheet: .sleep, size: CGSize(width: 48, height: 48), fps: 4, bundle: bundle)
                .position(x: 152, y: 148)
            Text("z z")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Theme.Colors.accent)
                .position(x: 176, y: 128)

            // The ball bounces from side to side on the floor.
            CatSpriteView(sheet: .ball, size: CGSize(width: 48, height: 32), fps: 5,
                          roll: CatSpriteRoll(distance: 60, duration: 2.5), bundle: bundle)
                .position(x: 150, y: 205)
        }
        .frame(width: roomSize, height: roomSize, alignment: .topLeading)
        .offset(y: roomTop)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

#Preview {
    SignInCatRoom()
}
