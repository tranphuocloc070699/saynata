//
//  MediaFileCache.swift
//  EnglishTranslation
//
//  Created for TASK-51 (media from the server URL).
//

import CryptoKit
import Foundation

/// A small disk cache for the photos and clips that come from the server.
///
/// Files live in the Caches folder (iOS may delete them when space is low, which is fine: they are downloaded again).
/// The folder never grows past `maxBytes`: when a new file does not fit, the file used longest ago is removed first.
actor MediaFileCache {
    enum CacheError: Error {
        case badResponse
        case tooBig
    }

    /// The cache the app uses: 50 MB in Caches/SenseMedia.
    static let shared = MediaFileCache(
        directory: URL.cachesDirectory.appending(path: "SenseMedia", directoryHint: .isDirectory),
        maxBytes: 50 * 1024 * 1024)

    private let directory: URL
    private let maxBytes: Int
    private let download: @Sendable (URL) async throws -> Data
    private var downloads: [URL: Task<URL, Error>] = [:]

    /// - Parameter download: gets the bytes of a web address. Tests give a fake one, so they need no network.
    init(
        directory: URL, maxBytes: Int,
        download: @escaping @Sendable (URL) async throws -> Data = MediaFileCache.downloadWithURLSession
    ) {
        self.directory = directory
        self.maxBytes = maxBytes
        self.download = download
    }

    /// The file for `url` if it was saved before, else nil. Marks the file as just used.
    func cachedFile(for url: URL) -> URL? {
        let file = fileURL(for: url)
        let path = file.path(percentEncoded: false)
        guard FileManager.default.fileExists(atPath: path) else { return nil }
        try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: path)
        return file
    }

    /// The local file for `url`: from the cache, or downloaded and saved first. Throws when the download fails.
    func file(for url: URL) async throws -> URL {
        if let cached = cachedFile(for: url) {
            return cached
        }
        // Two views asking for the same address share one download.
        if let running = downloads[url] {
            return try await running.value
        }
        let task = Task { try await self.downloadAndSave(url) }
        downloads[url] = task
        defer { downloads[url] = nil }
        return try await task.value
    }

    /// Saves `url` for next time. Errors are ignored: the clip still plays from the network this time.
    func prefetch(_ url: URL) async {
        _ = try? await file(for: url)
    }

    /// The total size of all cached files, in bytes.
    func totalBytes() -> Int {
        files().reduce(0) { $0 + $1.size }
    }

    // MARK: - Private

    /// The file name is a hash of the address, plus the extension (AVPlayer needs `.mp4` to recognise a clip).
    private func fileURL(for url: URL) -> URL {
        let hash = SHA256.hash(data: Data(url.absoluteString.utf8)).map { String(format: "%02x", $0) }.joined()
        let ext = url.pathExtension
        return directory.appending(path: ext.isEmpty ? hash : "\(hash).\(ext)")
    }

    private func downloadAndSave(_ url: URL) async throws -> URL {
        let data = try await download(url)
        // A file bigger than a quarter of the cache would push out everything else.
        guard !data.isEmpty, data.count <= maxBytes / 4 else { throw CacheError.tooBig }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = fileURL(for: url)
        try data.write(to: file, options: .atomic)
        trim(keeping: file)
        return file
    }

    /// Removes the least recently used files until the folder is within `maxBytes`. Never removes `keeping`.
    private func trim(keeping: URL) {
        var total = totalBytes()
        for old in files().sorted(by: { $0.date < $1.date }) where total > maxBytes {
            if old.url.lastPathComponent == keeping.lastPathComponent { continue }
            try? FileManager.default.removeItem(at: old.url)
            total -= old.size
        }
    }

    private func files() -> [(url: URL, size: Int, date: Date)] {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        let urls = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys)) ?? []
        return urls.map { url in
            let values = try? url.resourceValues(forKeys: Set(keys))
            return (url, values?.fileSize ?? 0, values?.contentModificationDate ?? .distantPast)
        }
    }

    /// The normal download: one GET, and the answer must be 2xx.
    static let downloadWithURLSession: @Sendable (URL) async throws -> Data = { url in
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CacheError.badResponse
        }
        return data
    }
}
