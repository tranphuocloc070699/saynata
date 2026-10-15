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
/// No access group is set on purpose. The free Apple ID cannot sign App Groups or shared Keychain groups
/// (`scripts/install-on-phone.sh`), so each target keeps its own Keychain items. The Share and Translation
/// extensions do not see the app's tokens, so they act as guests (doc-17, known gaps).
struct KeychainSecretStore: SecretStoring {
    /// One service name for all Saynata items. Tests use another name so they never touch the real tokens.
    var service = "com.luke.EnglishTranslation.account"

    struct KeychainError: LocalizedError {
        let status: OSStatus
        var errorDescription: String? { "Could not save to the Keychain (error \(status))." }
    }

    func secret(forKey key: String) -> String? {
        var query = baseQuery(forKey: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func setSecret(_ value: String?, forKey key: String) throws {
        let query = baseQuery(forKey: key)
        guard let value else {
            let status = SecItemDelete(query as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError(status: status) }
            return
        }

        let data = Data(value.utf8)
        let update = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if update == errSecSuccess { return }
        guard update == errSecItemNotFound else { throw KeychainError(status: update) }

        var item = query
        item[kSecValueData as String] = data
        // Readable after the first unlock (so a refresh in the background works), and never copied to another device.
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let add = SecItemAdd(item as CFDictionary, nil)
        guard add == errSecSuccess else { throw KeychainError(status: add) }
    }

    private func baseQuery(forKey key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}

/// Saves nothing. Previews use it, so they never touch the real Keychain.
struct NoSecretStore: SecretStoring {
    func secret(forKey key: String) -> String? { nil }
    func setSecret(_ value: String?, forKey key: String) throws {}
}
