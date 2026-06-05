import Foundation
import Testing
@testable import meApp

/// Unit tests for the 280-character limit enforced by `NotesInputField` and
/// the `AppInputField` textarea variant (MOB-437).
///
/// Because the truncation and counter logic lives inside SwiftUI `onChange`
/// closures and an overlay `Text`, the pure functions are extracted and tested
/// directly here — the same pattern used across this test suite (e.g.
/// `DateTimeToolsTests.swift`).
@Suite("Notes 280-char limit")
struct NotesCharacterLimitTests {

    private let maxCharacters = 280

    // MARK: - Helpers mirroring the production logic

    /// Mirrors the `onChange` guard in both `NotesInputField` and the
    /// `AppInputField` textarea variant.
    private func truncate(_ text: String) -> String {
        text.count > maxCharacters ? String(text.prefix(maxCharacters)) : text
    }

    /// Mirrors the counter `Text` format used in both components.
    private func counterText(for text: String) -> String {
        "\(text.count)/\(maxCharacters)"
    }

    /// Mirrors the foregroundStyle condition: error when `count >= maxCharacters`.
    private func isErrorState(for text: String) -> Bool {
        text.count >= maxCharacters
    }

    // MARK: - Truncation behaviour

    @Test("does not truncate text that is strictly under the limit")
    func belowLimit() {
        let input = String(repeating: "a", count: 279)
        #expect(truncate(input) == input)
        #expect(truncate(input).count == 279)
    }

    @Test("does not truncate text at exactly 280 chars")
    func atLimit() {
        let input = String(repeating: "b", count: 280)
        #expect(truncate(input) == input)
        #expect(truncate(input).count == 280)
    }

    @Test("truncates to exactly 280 when input is 281 chars")
    func oneOverLimit() {
        let input = String(repeating: "c", count: 281)
        let result = truncate(input)
        #expect(result.count == 280)
        #expect(result == String(repeating: "c", count: 280))
    }

    @Test("truncates to exactly 280 when input is well over the limit")
    func farOverLimit() {
        let input = String(repeating: "d", count: 500)
        let result = truncate(input)
        #expect(result.count == 280)
    }

    @Test("preserves the first 280 chars when truncating")
    func preservesLeadingContent() {
        // Build a string whose first 280 chars are distinct from the rest.
        let prefix = String(repeating: "x", count: 280)
        let input = prefix + String(repeating: "y", count: 50)
        let result = truncate(input)
        #expect(result == prefix)
    }

    // MARK: - Counter text format

    @Test("counter format is 'count/280' for empty string")
    func emptyStringCounter() {
        #expect(counterText(for: "") == "0/280")
    }

    @Test("counter format is 'count/280' for text below the limit")
    func belowLimitCounter() {
        let input = String(repeating: "e", count: 100)
        #expect(counterText(for: input) == "100/280")
    }

    @Test("counter format is '280/280' at exactly the limit")
    func atLimitCounter() {
        let input = String(repeating: "f", count: 280)
        #expect(counterText(for: input) == "280/280")
    }

    // MARK: - Error / normal state

    @Test("counter is in normal state (no error) for empty string")
    func emptyStringNormalState() {
        #expect(isErrorState(for: "") == false)
    }

    @Test("counter is in normal state below the limit")
    func normalStateBelowLimit() {
        let input = String(repeating: "g", count: 150)
        #expect(isErrorState(for: input) == false)
    }

    @Test("counter is in normal state at 279 chars")
    func normalStateOneUnderLimit() {
        let input = String(repeating: "h", count: 279)
        #expect(isErrorState(for: input) == false)
    }

    @Test("counter is in error state at exactly 280 chars")
    func errorStateAtLimit() {
        let input = String(repeating: "i", count: 280)
        #expect(isErrorState(for: input) == true)
    }

    // MARK: - Edge cases

    @Test("handles empty string without truncation")
    func emptyString() {
        #expect(truncate("") == "")
        #expect(truncate("").count == 0)
    }

    @Test("handles single character correctly")
    func singleCharacter() {
        let input = "A"
        #expect(truncate(input) == "A")
        #expect(counterText(for: input) == "1/280")
        #expect(isErrorState(for: input) == false)
    }

    @Test("multi-byte characters are counted by Swift character count")
    func multiByteCharacters() {
        // Swift's String.count counts Unicode grapheme clusters, which matches
        // the `.count` property used in both production components.
        let emoji = String(repeating: "😀", count: 280)
        #expect(truncate(emoji).count == 280)
        #expect(counterText(for: emoji) == "280/280")
        #expect(isErrorState(for: emoji) == true)
    }
}
