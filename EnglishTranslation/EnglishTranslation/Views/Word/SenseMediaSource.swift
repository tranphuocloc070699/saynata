//
//  SenseMediaSource.swift
//  EnglishTranslation
//
//  Created for TASK-51 (media from the server URL).
//

import Foundation

/// Where the photo or clip of a sense comes from.
///
/// `Sense.imageUrl` holds one of two things:
/// - a bundled path from the dictionary file, like `media/run_1.mp4` or `run_1.webp`. Only a few files are inside the
///   app, so `resolved` sends the other paths to the server (same file name under `/media/`).
/// - an absolute https address from the backend, like `https://api.saynata.com/media/run_1_3fa9c2d1.mp4`
nonisolated enum SenseMediaSource: Equatable, Sendable {
    case bundled(path: String)
    case remote(URL)

    /// Reads the text from `Sense.imageUrl`. Returns nil for empty text and for web addresses that are not https
    /// (iOS blocks plain http, so we do not try).
    init?(path: String) {
        let text = path.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if text.contains("://") {
            guard let url = URL(string: text), url.scheme?.lowercased() == "https", url.host() != nil else {
                return nil
            }
            self = .remote(url)
        } else {
            self = .bundled(path: text)
        }
    }

    /// True for an MP4 clip, false for a photo. The query part of a web address is ignored.
    var isClip: Bool {
        switch self {
        case .bundled(let path): return path.lowercased().hasSuffix(".mp4")
        case .remote(let url): return url.pathExtension.lowercased() == "mp4"
        }
    }

    /// The source to really load. A bundled path whose file is inside the app stays bundled. Any other bundled path
    /// becomes the server address `<serverBase>/media/<file>`, so the app does not need to carry every file.
    /// Returns nil when the file is not in the app and there is no server address. Remote sources stay as they are.
    func resolved(in bundle: Bundle = .main, serverBase: URL?) -> SenseMediaSource? {
        guard case .bundled(let path) = self else { return self }
        if bundledURL(in: bundle) != nil { return self }
        let fileName = (path as NSString).lastPathComponent
        guard let serverBase, !fileName.isEmpty, !path.hasSuffix("/") else { return nil }
        return .remote(serverBase.appending(path: "media").appending(path: fileName))
    }

    /// Finds a bundled file for `.bundled`. Tries the path as written, then the `media` folder, then the bundle root.
    func bundledURL(in bundle: Bundle = .main) -> URL? {
        guard case .bundled(let path) = self else { return nil }
        if let url = bundle.url(forResource: path, withExtension: nil) {
            return url
        }
        let fileName = (path as NSString).lastPathComponent
        let ext = (fileName as NSString).pathExtension
        let base = (fileName as NSString).deletingPathExtension
        if let url = bundle.url(forResource: base, withExtension: ext, subdirectory: "media") {
            return url
        }
        return bundle.url(forResource: base, withExtension: ext)
    }
}
