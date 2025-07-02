import SwiftUI

/// Helper extension to make it easier to bold specific words inside a string using `AttributedString`.
///
/// Usage:
/// ```swift
/// Text("Some text".asAttributed(withBoldWords: ["UP", "DOWN"]))
/// ```
extension String {
    /// Returns an `AttributedString` where every occurrence of the given `words` is rendered **bold**.
    /// - Parameters:
    ///   - words: The words that need to be bolded (case-insensitive).
    ///   - baseStyle: The typography style to use for the whole string. Defaults to `.body2`.
    /// - Returns: An `AttributedString` with the desired styling applied.
    func asAttributed(
        withBoldWords words: [String],
        baseStyle: CustomTextStyle = .body2
    ) -> AttributedString {
        var attributed = AttributedString(self)

        // Base font (OpenSans – Regular)
        let baseFont = Font.custom("OpenSans-Regular", size: baseStyle.size)
        attributed.font = baseFont

        // Bold font (same family, bold weight)
        let boldFont = baseFont.weight(.bold)

        for word in words {
            if let range = attributed.range(of: word, options: .caseInsensitive) {
                attributed[range].font = boldFont
            }
        }
        return attributed
    }
} 