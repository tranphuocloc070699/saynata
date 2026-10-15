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
/// - a bundled path from the old data, like `media/run_1.mp4` or `run_1.webp`
/// - an absolute https address from the backend, like `https://api.loffy.me/media/run_1_3fa9c2d1.mp4`
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
