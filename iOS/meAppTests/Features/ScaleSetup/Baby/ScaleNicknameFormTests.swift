//
//  ScaleNicknameFormTests.swift
//  meAppTests
//

import Combine
import Foundation
import Testing
@testable import meApp

struct ScaleNicknameFormTests {

    // MARK: - Initial State

    @Test("initial state: default nickname is Smart Baby Scale and form is valid")
    func initialState() {
        let form = ScaleNicknameForm()

        #expect(form.nickname.value == "Smart Baby Scale")
        #expect(form.isValid == true)
        #expect(form.isPristine == true)
    }

    // MARK: - Required Validation

    @Test("getError: returns required when nickname is empty")
    func nicknameRequired() {
        let form = ScaleNicknameForm()

        form.nickname.value = ""

        #expect(form.nickname.isInvalid == true)
        #expect(form.getError() == NicknameFormTestText.required)
    }

    // MARK: - Whitespace Validation

    @Test("getError: returns required for whitespace-only nickname")
    func nicknameWhitespaceOnly() {
        let form = ScaleNicknameForm()

        form.nickname.value = "   "

        #expect(form.getError() == NicknameFormTestText.noWhiteSpace)
    }

    // MARK: - Max Length Validation

    @Test("getError: returns max length error when exceeds 30 characters")
    func nicknameMaxLength() {
        let form = ScaleNicknameForm()

        form.nickname.value = String(repeating: "a", count: 31)

        #expect(form.getError() == NicknameFormTestText.maxLength30)
    }

    @Test("getError: returns nil for valid 30-char nickname")
    func nicknameExactlyMaxLength() {
        let form = ScaleNicknameForm()

        form.nickname.value = String(repeating: "a", count: 30)

        #expect(form.getError() == nil)
    }

    // MARK: - Reset

    @Test("reset: restores default nickname and marks as pristine")
    func resetRestoresDefault() {
        let form = ScaleNicknameForm()

        form.nickname.value = "Custom Name"
        form.reset()

        #expect(form.nickname.value == "Smart Baby Scale")
        #expect(form.nickname.isPristine == true)
    }

    // MARK: - formDidChange Publisher

    @Test("formDidChange: emits when nickname changes")
    func formDidChangeEmits() {
        let form = ScaleNicknameForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.nickname.value = "New Name"

        #expect(emissions >= 1)
    }
}

// MARK: - Test String Constants

private enum NicknameFormTestText {
    static let required = "This field is required"
    static let noWhiteSpace = "This field is required"
    static let maxLength30 = "maximum value should be 30"
}
