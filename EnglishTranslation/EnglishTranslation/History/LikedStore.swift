//
//  LikedStore.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// The server side of Liked: `GET`, `PUT` and `DELETE /api/liked` (TASK-93.1). `BackendClient` is the real one;
/// tests use a fake. All three throw `BackendError.signInRequired` for a guest.
protocol LikedAPI: Sendable {
    func likedItems() async throws -> [SavedItem]
    func like(_ item: SavedItem) async throws
    func unlike(kind: SavedItem.Kind, text: String) async throws
}

/// The copy of the liked list on the phone, so the stars and the Liked list show at once and offline.
protocol LikedCopyStoring: Sendable {
    func load() -> [SavedItem]
    func save(_ items: [SavedItem])
}

/// The copy as a JSON file in the App Group folder (the extensions read the same file).
struct LikedCopyFile: LikedCopyStoring {
    let fileURL: URL

    static let shared = LikedCopyFile(fileURL: ResultCache.cacheFolder().appending(path: "liked-copy.json"))

    func load() -> [SavedItem] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        return (try? JSONDecoder().decode([SavedItem].self, from: data)) ?? []
    }

    func save(_ items: [SavedItem]) {
        try? FileManager.default.createDirectory(
            at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(items) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}

/// The liked words and sentences of the signed-in user (TASK-93.2). Views read `items` and `isLiked`; the star calls
/// `toggle`. The change shows at once and is sent to the server; if the server refuses, it is undone.
@Observable
@MainActor
final class LikedStore {
    /// Newest first.
    private(set) var items: [SavedItem]
    /// True when a guest tapped the star. The main screen opens the sign-in sheet and sets it back to false.
    var signInRequested = false
    /// A short message when the last change did not reach the server. Cleared by the next change.
    private(set) var errorMessage: String?

    /// The like that waits for a sign-in. It is sent by `signedIn()`.
    private(set) var pending: SavedItem?

    @ObservationIgnored private let makeAPI: @MainActor () throws -> any LikedAPI
    @ObservationIgnored private let copy: any LikedCopyStoring
    @ObservationIgnored private let isSignedIn: @MainActor () -> Bool

    /// - Parameters:
    ///   - makeAPI: Builds the server client on each call, so a missing setting is an error, not a crash at launch.
    ///   - isSignedIn: True when there is a signed-in account. A guest never reaches the server.
    init(
        makeAPI: @escaping @MainActor () throws -> any LikedAPI,
        copy: any LikedCopyStoring,
        isSignedIn: @escaping @MainActor () -> Bool
    ) {
        self.makeAPI = makeAPI
        self.copy = copy
        self.isSignedIn = isSignedIn
        items = copy.load()
    }

    /// The real store: the backend client, the file in the App Group folder, and the shared account.
    static func live() -> LikedStore {
        LikedStore(
            makeAPI: {
                BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: nil, auth: AccountStore.shared)
            },
            copy: LikedCopyFile.shared,
            isSignedIn: { AccountStore.shared.isSignedIn })
    }

    func isLiked(_ item: SavedItem) -> Bool {
        items.contains { $0.id == item.id }
    }

    // MARK: - Star

    /// Likes the item, or unlikes it when it is liked. A guest gets the sign-in sheet instead, and the like
    /// is sent after sign-in.
    func toggle(_ item: SavedItem) async {
        errorMessage = nil
        guard isSignedIn() else {
            pending = item
            signInRequested = true
            return
        }
        if isLiked(item) {
            await unlike(id: item.id)
        } else {
            await like(item)
        }
    }

    private func like(_ item: SavedItem) async {
        let before = items
        setItems([item] + items.filter { $0.id != item.id })
        do {
            try await makeAPI().like(item)
        } catch {
            fail(error, undoTo: before, pendingLike: item)
        }
    }

    /// Unlikes by item id. Also used by the swipe in the Liked list.
    func unlike(id: String) async {
        guard let item = items.first(where: { $0.id == id }) else { return }
        errorMessage = nil
        let before = items
        setItems(items.filter { $0.id != id })
        do {
            try await makeAPI().unlike(kind: item.kind, text: item.text)
        } catch {
            fail(error, undoTo: before, pendingLike: nil)
        }
    }

    private func fail(_ error: Error, undoTo before: [SavedItem], pendingLike: SavedItem?) {
        setItems(before)
        if case BackendError.signInRequired = error {
            // The session ended: ask again for a sign-in, and send the like afterwards.
            pending = pendingLike
            signInRequested = true
        } else {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Sign-in and sign-out

    /// The user signed in: send the like that was waiting, then load the list from the server.
    func signedIn() async {
        let waiting = pending
        pending = nil
        if let waiting, !isLiked(waiting) {
            await like(waiting)
        }
        await refresh()
    }

    /// The sign-in sheet was closed without signing in.
    func cancelPending() {
        pending = nil
    }

    /// Replaces the list with the server's. The label of a word (`chip`) is not on the server, so it is kept from the
    /// copy on the phone. A failed call keeps the copy: the list still shows offline.
    func refresh() async {
        guard isSignedIn() else { return }
        do {
            let fromServer = try await makeAPI().likedItems()
            let chips = Dictionary(items.compactMap { item in item.chip.map { (item.id, $0) } },
                                   uniquingKeysWith: { first, _ in first })
            setItems(fromServer.map { item in
                SavedItem(kind: item.kind, text: item.text, preview: item.preview,
                          chip: chips[item.id], date: item.date)
            })
        } catch {
            // Offline or a server error: keep what we have.
        }
    }

    /// Signed out: the liked list belongs to the account, so it goes from this phone.
    func clear() {
        pending = nil
        errorMessage = nil
        setItems([])
    }

    private func setItems(_ new: [SavedItem]) {
        items = new
        copy.save(new)
    }
}
