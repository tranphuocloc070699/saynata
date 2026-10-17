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
/// - Size: the frame has the picture's own shape, fitted inside `maxSide` × `maxSide`, so nothing is cut off.
///   With `pixelSize` (from the dictionary or the backend) the shape is right before the file loads, so nothing jumps.
///   Without it, the frame is 16:9 until a photo loads, then takes the photo's shape. Clips stay 16:9.
/// - Loading: the frame keeps its size but is invisible. Failed: nothing is shown, no error.
/// - A clip plays only while about a third of it is on screen and the app is active (TASK-47).
struct SenseMediaView: View {
    let path: String
    /// The picture's size in pixels, when known.
    var pixelSize: CGSize?

    /// The longest side of the frame. A wide picture is this wide, a tall one this tall.
    static let maxSide: CGFloat = 240
    /// The frame when the size is not known yet (and for clips): 240 × 135.
    static let defaultSize = CGSize(width: 240, height: 135)

    /// The frame for a picture of `pixelSize`: same shape, fitted inside `maxSide` × `maxSide`.
    static func frameSize(for pixelSize: CGSize?) -> CGSize {
        guard let pixelSize, pixelSize.width > 0, pixelSize.height > 0 else { return defaultSize }
        let scale = maxSide / max(pixelSize.width, pixelSize.height)
        return CGSize(width: (pixelSize.width * scale).rounded(), height: (pixelSize.height * scale).rounded())
    }

    /// How much of the frame must be visible before a clip plays.
    private static let visibleShare = 0.33

    private enum LoadState {
        case loading
        case photo(PlatformImage)
        case clip(URL)
        case failed
    }

    @State private var loadState = LoadState.loading
    @State private var isOnScreen = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        if let source = SenseMediaSource(path: path), !isFailed {
            let size = Self.frameSize(for: pixelSize ?? loadedPhotoSize)
            content
                .frame(width: size.width, height: size.height)
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
            Image(platformImage: image)
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

    /// The loaded photo's size, used only when `pixelSize` was not given.
    private var loadedPhotoSize: CGSize? {
        if case .photo(let image) = loadState { return image.size }
        return nil
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
        PlatformImage(contentsOfFile: file.path(percentEncoded: false)).map(LoadState.photo) ?? .failed
    }
}

/// A muted, continuous video loop for action clips using AVPlayerLayer.
private struct LoopingVideoView {
    let url: URL
    let isPlaying: Bool
    let onFailure: () -> Void

    fileprivate func makePlayerView() -> LoopingPlayerUIView {
        let view = LoopingPlayerUIView()
        view.onFailure = onFailure
        view.load(url: url)
        view.setPlaying(isPlaying)
        return view
    }

    fileprivate func updatePlayerView(_ view: LoopingPlayerUIView) {
        view.onFailure = onFailure
        view.setPlaying(isPlaying)
    }
}

#if os(iOS)
extension LoopingVideoView: UIViewRepresentable {
    func makeUIView(context: Context) -> LoopingPlayerUIView { makePlayerView() }
    func updateUIView(_ uiView: LoopingPlayerUIView, context: Context) { updatePlayerView(uiView) }
}

private typealias PlayerHostView = UIView
#elseif os(macOS)
extension LoopingVideoView: NSViewRepresentable {
    func makeNSView(context: Context) -> LoopingPlayerUIView { makePlayerView() }
    func updateNSView(_ nsView: LoopingPlayerUIView, context: Context) { updatePlayerView(nsView) }
}

private typealias PlayerHostView = NSView
#endif

private final class LoopingPlayerUIView: PlayerHostView {
    var onFailure: (() -> Void)?
    private var queuePlayer: AVQueuePlayer?
    private var looper: AVPlayerLooper?
    private var statusObservation: NSKeyValueObservation?

    #if os(iOS)
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
    #elseif os(macOS)
    // An AppKit view gets its layer from `makeBackingLayer`, not `layerClass`.
    private let playerLayer = AVPlayerLayer()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }

    override func makeBackingLayer() -> CALayer {
        playerLayer
    }
    #endif

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
