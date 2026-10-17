//
//  SecretStore.swift
//  EnglishTranslation
//

import Foundation
import Security

/// A place for small secrets, such as the sign-in tokens. The real one is `KeychainSecretStore`; tests use a fake.
protocol SecretStoring: Sendable {
    /// The saved text for `key`, or nil when nothing is saved (or it cannot be read).
    func secret(forKey key: String) -> String?
    /// Saves `value` for `key`. A nil value deletes it.
    func setSecret(_ value: String?, forKey key: String) throws
}

/// Saves secrets in the iOS Keychain, not in UserDefaults (UserDefaults is a plain file that a backup can expose).
///
/// On iOS the items live in the App Group's Keychain group (`ResultCache.appGroupID`; an App Group name works as a
/// Keychain access group), so the app, the Share extension and the Translation extension share one sign-in.
/// A build without App Groups (a free Apple ID, `scripts/install-on-phone.sh` without `PAID_ACCOUNT=1`) cannot use
/// the group: each target then keeps its own items and the extensions act as guests, as before (doc-17).
/// An item saved by an older build (the app's own group) is moved to the shared group the first time it is read.
struct KeychainSecretStore: SecretStoring {
    /// One service name for all Saynata items. Tests use another name so they never touch the real tokens.
    var service = "com.luke.EnglishTranslation.account"
    /// The shared Keychain group, or nil for the target's own group. The Mac app has no extensions, so it keeps its own.
    #if os(iOS)
    var accessGroup: String? = ResultCache.appGroupID
    #else
    var accessGroup: String?
    #endif

    struct KeychainError: LocalizedError {
        let status: OSStatus
        var errorDescription: String? { "Could not save to the Keychain (error \(status))." }
    }

    func secret(forKey key: String) -> String? {
        if let accessGroup, let shared = read(baseQuery(forKey: key, group: accessGroup)) {
            return shared
        }
        // Without a group the search covers every group this target can read: the old item, or (without the
        // App Group entitlement) the only one there is.
        guard let found = read(baseQuery(forKey: key, group: nil)) else { return nil }
        if accessGroup != nil {
            try? setSecret(found, forKey: key)    // move it to the shared group
        }
        return found
    }

    func setSecret(_ value: String?, forKey key: String) throws {
        guard let value else {
            // Without a group this deletes the item in every group (shared and old).
            let status = SecItemDelete(baseQuery(forKey: key, group: nil) as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError(status: status) }
            return
        }
        let data = Data(value.utf8)
        if let accessGroup {
            let status = save(data, forKey: key, group: accessGroup)
            if status == errSecSuccess {
                deleteOutside(group: accessGroup, key: key)
                return
            }
            // No App Group entitlement in this build: fall back to the target's own group.
            guard status == errSecMissingEntitlement else { throw KeychainError(status: status) }
        }
        let status = save(data, forKey: key, group: nil)
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    // MARK: - Helpers

    private func read(_ query: [String: Any]) -> String? {
        var query = query
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Updates the item in `group` (nil: the target's own group), or adds it.
    private func save(_ data: Data, forKey key: String, group: String?) -> OSStatus {
        let query = baseQuery(forKey: key, group: group)
        let update = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if update != errSecItemNotFound { return update }
        var item = query
        item[kSecValueData as String] = data
        // Readable after the first unlock (so a refresh in the background works), and never copied to another device.
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(item as CFDictionary, nil)
    }

    /// Deletes copies of `key` in other groups (the item an older build saved in the app's own group).
    private func deleteOutside(group: String, key: String) {
        var query = baseQuery(forKey: key, group: nil)
        query[kSecReturnAttributes as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitAll
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let items = result as? [[String: Any]]
        else { return }
        for item in items {
            guard let other = item[kSecAttrAccessGroup as String] as? String, other != group else { continue }
            SecItemDelete(baseQuery(forKey: key, group: other) as CFDictionary)
        }
    }

    private func baseQuery(forKey key: String, group: String?) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        if let group { query[kSecAttrAccessGroup as String] = group }
        return query
    }
}

/// Saves nothing. Previews use it, so they never touch the real Keychain.
struct NoSecretStore: SecretStoring {
    func secret(forKey key: String) -> String? { nil }
    func setSecret(_ value: String?, forKey key: String) throws {}
}
