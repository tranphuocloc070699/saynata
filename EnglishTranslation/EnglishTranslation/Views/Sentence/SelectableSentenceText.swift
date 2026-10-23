//
//  SelectableSentenceText.swift
//  EnglishTranslation
//

import SwiftUI

/// The two sizes of sentence text in the result card (TASK-89.2), both serif and both Dynamic Type.
enum SentenceTextStyle {
    /// The sentence the user typed: `.title3`.
    case original
    /// The simple summary, and the text in the popup: `.body`.
    case summary

    var font: Font {
        switch self {
        case .original: .system(.title3, design: .serif)
        case .summary: .system(.body, design: .serif)
        }
    }
}

/// A selection the user made in a sentence: what it means for the app, and where it is (for the popup).
struct SentenceSelection: Equatable, Identifiable {
    let part: SentencePart
    /// Around the selected text, in the coordinates of the text view.
    let rect: CGRect

    var id: String {
        switch part {
        case let .word(token): "word:\(token.range.location):\(token.range.length)"
        case let .phrase(text): "phrase:\(text)"
        }
    }
}

#if os(iOS)
import UIKit

/// Sentence text where every word is tappable and a part can be selected (TASK-89.2).
///
/// SwiftUI's selectable `Text` cannot say what is selected, so this wraps a `UITextView`:
/// not editable, selectable, no scrolling, and it grows to the height of its text.
/// - Every word has a light dotted underline. A tap on a word calls `onWordTap`.
/// - When the selection stops changing (0.4 s), `onSelectionSettled` gets the part and where it is.
///   The system edit menu is off: the popup that opens has its own Copy button.
struct SelectableSentenceText: UIViewRepresentable {
    let text: String
    let style: SentenceTextStyle
    /// Bold key words and bullet lines of a summary (TASK-89.3), as ranges in `text`. Empty for plain text.
    var boldRanges: [NSRange] = []
    var bulletRanges: [NSRange] = []
    /// The word the user tapped, shown with the accent colour and a tinted background. Nil for none.
    var highlightedRange: NSRange?
    /// False in the popup: words can be tapped, but no new selection starts.
    var allowsSelection = true
    /// Raise this number to clear the selection (the popup closed).
    var clearSelectionCount = 0
    var onWordTap: (SentenceToken) -> Void = { _ in }
    var onSelectionSettled: (SentenceSelection) -> Void = { _ in }

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> UITextView {
        // TextKit 1, so the tap can ask the layout manager where each word is.
        let textView = SentenceUITextView(usingTextLayoutManager: false)
        textView.isEditable = false
        textView.isScrollEnabled = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.dataDetectorTypes = []
        textView.adjustsFontForContentSizeCategory = true
        textView.delegate = context.coordinator
        // Let SwiftUI decide the width; the height comes from `sizeThatFits`.
        textView.setContentHuggingPriority(.defaultLow, for: .horizontal)
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        tap.delegate = context.coordinator
        textView.addGestureRecognizer(tap)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        textView.isSelectable = allowsSelection
        coordinator.render(in: textView)

        if coordinator.lastClearCount != clearSelectionCount {
            coordinator.lastClearCount = clearSelectionCount
            textView.selectedTextRange = nil
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let proposed = proposal.width ?? 320
        let width = proposed.isFinite && proposed > 0 ? proposed : 320
        let fitted = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(fitted.height))
    }

    // MARK: - Text

    /// The serif font for `style` at the user's Dynamic Type size.
    private func uiFont() -> UIFont {
        let traits = UITraitCollection(preferredContentSizeCategory: Self.contentSizeCategory(for: dynamicTypeSize))
        let textStyle: UIFont.TextStyle = style == .original ? .title3 : .body
        let base = UIFont.preferredFont(forTextStyle: textStyle, compatibleWith: traits)
        let descriptor = base.fontDescriptor.withDesign(.serif) ?? base.fontDescriptor
        return UIFont(descriptor: descriptor, size: base.pointSize)
    }

    /// SwiftUI's Dynamic Type size as the UIKit size category.
    private static func contentSizeCategory(for size: DynamicTypeSize) -> UIContentSizeCategory {
        switch size {
        case .xSmall: .extraSmall
        case .small: .small
        case .medium: .medium
        case .large: .large
        case .xLarge: .extraLarge
        case .xxLarge: .extraExtraLarge
        case .xxxLarge: .extraExtraExtraLarge
        case .accessibility1: .accessibilityMedium
        case .accessibility2: .accessibilityLarge
        case .accessibility3: .accessibilityExtraLarge
        case .accessibility4: .accessibilityExtraExtraLarge
        case .accessibility5: .accessibilityExtraExtraExtraLarge
        @unknown default: .large
        }
    }

    /// The text with its bold words and bullet lines, the dotted underlines and, for the tapped word, the highlight.
    fileprivate func attributedText(tokens: [SentenceToken]) -> NSAttributedString {
        let font = uiFont()
        let result = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: UIColor(Theme.Colors.textPrimary),
            ])
        let length = result.length
        if !boldRanges.isEmpty {
            let descriptor = font.fontDescriptor.withSymbolicTraits(.traitBold) ?? font.fontDescriptor
            let bold = UIFont(descriptor: descriptor, size: font.pointSize)
            for range in boldRanges where NSMaxRange(range) <= length {
                result.addAttribute(.font, value: bold, range: range)
            }
        }
        if !bulletRanges.isEmpty {
            // "•", a tab, then the line; a wrapped line starts under the text, not under the bullet.
            let indent = ceil(font.pointSize * 1.1)
            let paragraph = NSMutableParagraphStyle()
            paragraph.tabStops = [NSTextTab(textAlignment: .left, location: indent)]
            paragraph.defaultTabInterval = indent
            paragraph.headIndent = indent
            paragraph.paragraphSpacingBefore = font.pointSize * 0.3
            for range in bulletRanges where NSMaxRange(range) <= length {
                result.addAttribute(.paragraphStyle, value: paragraph, range: range)
            }
        }
        let dotted = NSUnderlineStyle.single.rawValue | NSUnderlineStyle.patternDot.rawValue
        for token in tokens {
            if let highlightedRange, NSEqualRanges(highlightedRange, token.range) {
                result.addAttributes(
                    [
                        .backgroundColor: UIColor(Theme.Colors.chipFill),
                        .foregroundColor: UIColor(Theme.Colors.accent),
                    ],
                    range: token.range)
            } else {
                result.addAttributes(
                    [
                        .underlineStyle: dotted,
                        .underlineColor: UIColor(Theme.Colors.textMuted),
                    ],
                    range: token.range)
            }
        }
        return result
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, UITextViewDelegate, UIGestureRecognizerDelegate {
        var parent: SelectableSentenceText
        var lastClearCount: Int
        private var tokens: [SentenceToken] = []
        private var tokenizedText: String?
        /// What the view shows now. When nothing changed, the text view is left alone, so a selection in progress
        /// is not disturbed by an unrelated SwiftUI update.
        private var appliedLook: Look?

        /// Everything that changes how the text looks.
        private struct Look: Equatable {
            let text: String
            let boldRanges: [NSRange]
            let style: SentenceTextStyle
            let highlightedRange: NSRange?
            let dynamicTypeSize: DynamicTypeSize
        }
        /// True while the app itself changes the text, so that does not count as the user selecting.
        private var isApplying = false
        private let debouncer = SelectionDebouncer()

        init(_ parent: SelectableSentenceText) {
            self.parent = parent
            lastClearCount = parent.clearSelectionCount
        }

        /// Puts the text and its attributes in the view, only when something changed. The selection is kept.
        func render(in textView: UITextView) {
            if tokenizedText != parent.text {
                tokens = SentenceTokenizer.tokens(in: parent.text)
                tokenizedText = parent.text
            }
            let look = Look(
                text: parent.text, boldRanges: parent.boldRanges, style: parent.style,
                highlightedRange: parent.highlightedRange, dynamicTypeSize: parent.dynamicTypeSize)
            guard look != appliedLook else { return }
            appliedLook = look
            let attributed = parent.attributedText(tokens: tokens)

            let selection = textView.selectedRange
            let sameText = textView.text == parent.text
            isApplying = true
            textView.attributedText = attributed
            if sameText, NSMaxRange(selection) <= (parent.text as NSString).length {
                textView.selectedRange = selection
            }
            isApplying = false
        }

        // MARK: Word tap

        @objc func handleTap(_ recognizer: UITapGestureRecognizer) {
            guard let textView = recognizer.view as? UITextView else { return }
            let point = recognizer.location(in: textView)
            guard let token = token(at: point, in: textView) else { return }
            parent.onWordTap(token)
        }

        /// The word under `point`, or nil when the tap landed on a space, a mark or empty space.
        private func token(at point: CGPoint, in textView: UITextView) -> SentenceToken? {
            let layout = textView.layoutManager
            let container = textView.textContainer
            let inContainer = CGPoint(
                x: point.x - textView.textContainerInset.left,
                y: point.y - textView.textContainerInset.top)
            let index = layout.characterIndex(
                for: inContainer, in: container, fractionOfDistanceBetweenInsertionPoints: nil)
            guard let token = SentenceTokenizer.token(at: index, in: tokens) else { return nil }

            // `characterIndex` answers the nearest character, even far from the text. Check the tap is on the word.
            let glyphs = layout.glyphRange(forCharacterRange: token.range, actualCharacterRange: nil)
            var isOnWord = false
            layout.enumerateEnclosingRects(
                forGlyphRange: glyphs,
                withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0),
                in: container
            ) { rect, _ in
                if rect.insetBy(dx: -6, dy: -6).contains(inContainer) { isOnWord = true }
            }
            return isOnWord ? token : nil
        }

        // The tap must not stop the text view's own touches (selection handles, long press).
        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            true
        }

        // MARK: Selection

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard !isApplying else { return }
            guard textView.selectedRange.length > 0 else {
                debouncer.cancel()
                return
            }
            debouncer.submit { [weak self, weak textView] in
                guard let self, let textView else { return }
                self.settle(in: textView)
            }
        }

        private func settle(in textView: UITextView) {
            guard
                let range = textView.selectedTextRange,
                let part = SentencePart.from(range: textView.selectedRange, in: textView.text)
            else { return }
            let rects = textView.selectionRects(for: range).map(\.rect).filter { !$0.isEmpty }
            guard let first = rects.first else { return }
            let union = rects.dropFirst().reduce(first) { $0.union($1) }
            parent.onSelectionSettled(SentenceSelection(part: part, rect: union))
        }

        /// The system menu is replaced by the popup (which has its own Copy button).
        func textView(
            _ textView: UITextView,
            editMenuForTextIn range: NSRange,
            suggestedActions: [UIMenuElement]
        ) -> UIMenu? {
            UIMenu(children: [])
        }
    }
}

/// A text view without the system edit menu (Copy, Look Up, Share…).
private final class SentenceUITextView: UITextView {
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        false
    }
}

#else

/// The Mac version (TASK-89.2): words are tappable links (dotted underline), and the selection uses the normal
/// Mac menu. There is no popup for a selected part on the Mac yet.
struct SelectableSentenceText: View {
    let text: String
    let style: SentenceTextStyle
    var boldRanges: [NSRange] = []
    /// Bullet lines start with "•" and a tab in `text`; the Mac shows them without a hanging indent.
    var bulletRanges: [NSRange] = []
    var highlightedRange: NSRange?
    var allowsSelection = true
    var clearSelectionCount = 0
    var onWordTap: (SentenceToken) -> Void = { _ in }
    var onSelectionSettled: (SentenceSelection) -> Void = { _ in }

    var body: some View {
        let tokens = SentenceTokenizer.tokens(in: text)
        Text(attributedText(tokens: tokens))
            .font(style.font)
            .foregroundStyle(Theme.Colors.textPrimary)
            .tint(Theme.Colors.textPrimary)
            .textSelection(.enabled)
            .fixedSize(horizontal: false, vertical: true)
            .environment(\.openURL, OpenURLAction { url in
                guard
                    url.scheme == "saynata-word",
                    let offset = Int(url.host() ?? ""),
                    let token = SentenceTokenizer.token(at: offset, in: tokens)
                else { return .discarded }
                onWordTap(token)
                return .handled
            })
    }

    /// Each word is a link to `saynata-word://<offset>`, drawn with a dotted underline.
    private func attributedText(tokens: [SentenceToken]) -> AttributedString {
        let nsText = NSMutableAttributedString(string: text)
        for range in boldRanges where NSMaxRange(range) <= nsText.length {
            nsText.addAttribute(
                .inlinePresentationIntent, value: InlinePresentationIntent.stronglyEmphasized.rawValue, range: range)
        }
        for token in tokens {
            nsText.addAttribute(.link, value: URL(string: "saynata-word://\(token.range.location)") as Any, range: token.range)
        }
        var attributed = (try? AttributedString(nsText, including: \.foundation)) ?? AttributedString(nsText)
        for run in attributed.runs where run.link != nil {
            guard let link = run.link, let offset = Int(link.host() ?? "") else { continue }
            let isHighlighted = highlightedRange.map { $0.location == offset } ?? false
            attributed[run.range].underlineStyle = isHighlighted ? nil : Text.LineStyle(pattern: .dot, color: Theme.Colors.textMuted)
            if isHighlighted {
                attributed[run.range].backgroundColor = Theme.Colors.chipFill
                attributed[run.range].foregroundColor = Theme.Colors.accent
            }
        }
        return attributed
    }
}
#endif
