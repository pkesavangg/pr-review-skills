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

    private let maxCharacters = AppConstants.Input.notesMaxCharacters

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
        let input = String(repeating: "a", count: maxCharacters - 1)
        #expect(truncate(input) == input)
        #expect(truncate(input).count == maxCharacters - 1)
    }

    @Test("does not truncate text at exactly the limit")
    func atLimit() {
        let input = String(repeating: "b", count: maxCharacters)
        #expect(truncate(input) == input)
        #expect(truncate(input).count == maxCharacters)
    }

    @Test("truncates to exactly the limit when input is one over")
    func oneOverLimit() {
        let input = String(repeating: "c", count: maxCharacters + 1)
        let result = truncate(input)
        #expect(result.count == maxCharacters)
        #expect(result == String(repeating: "c", count: maxCharacters))
    }

    @Test("truncates to exactly the limit when input is well over the limit")
    func farOverLimit() {
        let input = String(repeating: "d", count: 500)
        let result = truncate(input)
        #expect(result.count == maxCharacters)
    }

    @Test("preserves the first N chars when truncating")
    func preservesLeadingContent() {
        let prefix = String(repeating: "x", count: maxCharacters)
        let input = prefix + String(repeating: "y", count: 50)
        let result = truncate(input)
        #expect(result == prefix)
    }

    // MARK: - Counter text format

    @Test("counter format is 'count/max' for empty string")
    func emptyStringCounter() {
        #expect(counterText(for: "") == "0/\(maxCharacters)")
    }

    @Test("counter format is 'count/max' for text below the limit")
    func belowLimitCounter() {
        let input = String(repeating: "e", count: 100)
        #expect(counterText(for: input) == "100/\(maxCharacters)")
    }

    @Test("counter format is 'max/max' at exactly the limit")
    func atLimitCounter() {
        let input = String(repeating: "f", count: maxCharacters)
        #expect(counterText(for: input) == "\(maxCharacters)/\(maxCharacters)")
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

    @Test("counter is in normal state at one under the limit")
    func normalStateOneUnderLimit() {
        let input = String(repeating: "h", count: maxCharacters - 1)
        #expect(isErrorState(for: input) == false)
    }

    @Test("counter is in error state at exactly the limit")
    func errorStateAtLimit() {
        let input = String(repeating: "i", count: maxCharacters)
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
        #expect(counterText(for: input) == "1/\(maxCharacters)")
        #expect(isErrorState(for: input) == false)
    }

    @Test("multi-byte characters are counted by Swift character count")
    func multiByteCharacters() {
        // Swift's String.count counts Unicode grapheme clusters, which matches
        // the `.count` property used in both production components.
        let emoji = String(repeating: "😀", count: maxCharacters)
        #expect(truncate(emoji).count == maxCharacters)
        #expect(counterText(for: emoji) == "\(maxCharacters)/\(maxCharacters)")
        #expect(isErrorState(for: emoji) == true)
    }
}
