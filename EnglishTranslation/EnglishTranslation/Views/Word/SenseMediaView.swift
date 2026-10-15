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
///
/// The path is a bundled path or an https address from the backend (TASK-51).
/// - Bundled path: the file inside the app if it is there, else the same file from the server (`SenseMediaSource.resolved`).
/// - Server photo: saved in `MediaFileCache`, so it shows again offline after the first view.
/// - Server clip: streamed by AVPlayer; the file is saved for next time. A saved file plays instead of the stream.
/// - Loading: the frame keeps its size but is invisible, so nothing jumps. Failed: nothing is shown, no error.
/// - A clip plays only while about a third of it is on screen and the app is active (TASK-47).
struct SenseMediaView: View {
    let path: String

    private static let mediaWidth: CGFloat = 240
    private static let mediaHeight: CGFloat = 135

    /// How much of the frame must be visible before a clip plays.
    private static let visibleShare = 0.33

    private enum LoadState {
        case loading
        case photo(UIImage)
        case clip(URL)
        case failed
    }

    @State private var loadState = LoadState.loading
    @State private var isOnScreen = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        if let source = SenseMediaSource(path: path), !isFailed {
            content
                .frame(width: Self.mediaWidth, height: Self.mediaHeight)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.block))
                .overlay {
                    RoundedRectangle(cornerRadius: Theme.Radius.block)
                        .strokeBorder(Theme.Colors.hairline, lineWidth: 1)
                }
                .opacity(isLoading ? 0 : 1)
                .onScrollVisibilityChange(threshold: Self.visibleShare) { isOnScreen = $0 }
                .accessibilityHidden(true)
                .task(id: path) { await load(source) }
        }
    }

    @ViewBuilder private var content: some View {
        switch loadState {
        case .photo(let image):
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
        case .clip(let url):
            LoopingVideoView(
                url: url, isPlaying: isOnScreen && scenePhase == .active,
                onFailure: { loadState = .failed })
        case .loading, .failed:
            Color.clear
        }
    }

    private var isLoading: Bool {
        if case .loading = loadState { return true }
        return false
    }

    private var isFailed: Bool {
        if case .failed = loadState { return true }
        return false
    }

    private func load(_ firstSource: SenseMediaSource) async {
        // The server address comes from the same Info.plist value as the backend client.
        let serverBase = try? AppConfig.backendBaseURL()
        guard let source = firstSource.resolved(serverBase: serverBase) else {
            loadState = .failed
            return
        }
        switch source {
        case .bundled:
            guard let url = source.bundledURL() else {
                loadState = .failed
                return
            }
            loadState = source.isClip ? .clip(url) : Self.photoState(file: url)
        case .remote(let url):
            if source.isClip {
                await loadRemoteClip(url)
            } else if let file = try? await MediaFileCache.shared.file(for: url) {
                loadState = Self.photoState(file: file)
            } else {
                loadState = .failed
            }
        }
    }

    private func loadRemoteClip(_ url: URL) async {
        if let saved = await MediaFileCache.shared.cachedFile(for: url) {
            loadState = .clip(saved)
            return
        }
        // Play the stream now and save the file in the background for next time.
        loadState = .clip(url)
        await MediaFileCache.shared.prefetch(url)
    }

    private static func photoState(file: URL) -> LoadState {
        UIImage(contentsOfFile: file.path(percentEncoded: false)).map(LoadState.photo) ?? .failed
    }
}

/// A muted, continuous video loop for action clips using AVPlayerLayer.
private struct LoopingVideoView: UIViewRepresentable {
    let url: URL
    let isPlaying: Bool
    let onFailure: () -> Void

    func makeUIView(context: Context) -> LoopingPlayerUIView {
        let view = LoopingPlayerUIView()
        view.onFailure = onFailure
        view.load(url: url)
        view.setPlaying(isPlaying)
        return view
    }

    func updateUIView(_ uiView: LoopingPlayerUIView, context: Context) {
        uiView.onFailure = onFailure
        uiView.setPlaying(isPlaying)
    }
}

private final class LoopingPlayerUIView: UIView {
    var onFailure: (() -> Void)?
    private var queuePlayer: AVQueuePlayer?
    private var looper: AVPlayerLooper?
    private var statusObservation: NSKeyValueObservation?

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

    /// Sets up the loop but does not start it. Call `setPlaying(_:)` for that.
    func load(url: URL) {
        let item = AVPlayerItem(url: url)
        // Apple's pattern: an empty queue player; the looper adds the items itself.
        let player = AVQueuePlayer()
        player.isMuted = true
        let looper = AVPlayerLooper(player: player, templateItem: item)
        statusObservation = looper.observe(\.status, options: [.new]) { [weak self] looper, _ in
            guard looper.status == .failed else { return }
            DispatchQueue.main.async { self?.onFailure?() }
        }
        playerLayer.player = player
        playerLayer.videoGravity = .resizeAspectFill
        self.looper = looper
        self.queuePlayer = player
    }

    func setPlaying(_ isPlaying: Bool) {
        if isPlaying {
            queuePlayer?.play()
        } else {
            queuePlayer?.pause()
        }
    }
}
