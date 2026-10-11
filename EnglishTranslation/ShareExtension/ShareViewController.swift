//
//  ShareViewController.swift
//  ShareExtension
//

import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The entry point of the share extension (`NSExtensionPrincipalClass` in Info.plist).
/// iOS shows this view controller in a sheet over the host app. It only hosts the SwiftUI sheet,
/// reads the shared text, and closes or opens the app when a button is tapped.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        let sheet = ShareSheetView(
            loadText: { [weak self] in await self?.readSharedText() ?? "" },
            onOpenApp: { [weak self] url in self?.openApp(at: url) },
            onClose: { [weak self] in self?.close() })
        let host = UIHostingController(rootView: sheet)

        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        host.didMove(toParent: self)
    }

    // MARK: - Reading the text

    /// The text the user selected in the host app. Empty if there is none.
    private func readSharedText() async -> String {
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        for item in items {
            for provider in item.attachments ?? [] where provider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
                if let text = await Self.text(from: provider) { return text }
            }
            // Some apps only fill this field.
            if let text = item.attributedContentText?.string, !text.isEmpty { return text }
        }
        return ""
    }

    private static func text(from provider: NSItemProvider) async -> String? {
        guard let value = try? await provider.loadItem(forTypeIdentifier: UTType.text.identifier) else { return nil }
        switch value {
        case let string as String: return string
        case let attributed as NSAttributedString: return attributed.string
        case let data as Data: return String(data: data, encoding: .utf8)
        default: return nil
        }
    }

    // MARK: - Buttons

    /// Closes the sheet and returns to the host app.
    private func close() {
        extensionContext?.completeRequest(returningItems: nil)
    }

    /// Opens the Saynata app, then closes the sheet.
    ///
    /// A share extension may not call `UIApplication.shared`, and `extensionContext.open(_:)` only works for
    /// some extension types (not share extensions). The usual workaround is to find the host's
    /// `UIApplication` object in the responder chain and ask it to open the URL.
    private func openApp(at url: URL) {
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                break
            }
            responder = current.next
        }
        close()
    }
}
