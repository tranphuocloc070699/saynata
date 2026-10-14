//
//  SenseMediaView.swift
//  EnglishTranslation
//
//  Created for TASK-23 (Pixabay media preview).
//

import AVKit
import SwiftUI

/// Displays a small visual media asset (WebP photo or looping MP4 action clip) for a dictionary sense.
/// Placed below the definition to prevent layout shifts.
struct SenseMediaView: View {
    let path: String

    private static let mediaWidth: CGFloat = 240
    private static let mediaHeight: CGFloat = 135

    var body: some View {
        if let url = resolveMediaURL(path) {
            Group {
                if path.hasSuffix(".mp4") {
                    LoopingVideoView(url: url)
                } else if let image = UIImage(contentsOfFile: url.path) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                }
            }
            .frame(width: Self.mediaWidth, height: Self.mediaHeight)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.block))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.block)
                    .strokeBorder(Theme.Colors.hairline, lineWidth: 1)
            }
            .accessibilityHidden(true)
        }
    }

    private func resolveMediaURL(_ path: String) -> URL? {
        if let url = Bundle.main.url(forResource: path, withExtension: nil) {
            return url
        }
        let fileName = (path as NSString).lastPathComponent
        let ext = (fileName as NSString).pathExtension
        let base = (fileName as NSString).deletingPathExtension
        if let url = Bundle.main.url(forResource: base, withExtension: ext, subdirectory: "media") {
            return url
        }
        return Bundle.main.url(forResource: base, withExtension: ext)
    }
}

/// A muted, continuous video loop for action clips using AVPlayerLayer.
private struct LoopingVideoView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> LoopingPlayerUIView {
        let view = LoopingPlayerUIView()
        view.play(url: url)
        return view
    }

    func updateUIView(_ uiView: LoopingPlayerUIView, context: Context) {}
}

private final class LoopingPlayerUIView: UIView {
    private var queuePlayer: AVQueuePlayer?
    private var looper: AVPlayerLooper?

    override static var layerClass: AnyClass {
        AVPlayerLayer.self
    }

    private var playerLayer: AVPlayerLayer {
        layer as! AVPlayerLayer
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer.videoGravity = .resizeAspectFill
    }

    func play(url: URL) {
        let item = AVPlayerItem(url: url)
        let player = AVQueuePlayer(playerItem: item)
        player.isMuted = true
        looper = AVPlayerLooper(player: player, templateItem: item)
        playerLayer.player = player
        playerLayer.videoGravity = .resizeAspectFill
        player.play()
        self.queuePlayer = player
    }
}
