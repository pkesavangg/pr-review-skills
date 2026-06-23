//
//  UserNameFormTests.swift
//  meAppTests
//

import Testing
@testable import meApp

@Suite("UserNameForm", .serialized)
@MainActor
struct UserNameFormTests {

    // MARK: - Initial state

    @Test("initial displayName value is empty")
    func initialDisplayNameEmpty() {
        let form = UserNameForm()
        #expect(form.displayName.value == "")
    }

    @Test("initial form is invalid because displayName is required")
    func initialFormInvalid() {
        let form = UserNameForm()
        form.displayName.validate()
        #expect(form.displayName.errors[.required] == true)
    }

    @Test("initial userList is empty")
    func initialUserListEmpty() {
        let form = UserNameForm()
        #expect(form.userList.isEmpty)
    }

    // MARK: - Required validator

    @Test("empty displayName fails required")
    func emptyNameFailsRequired() {
        let form = UserNameForm()
        form.setDisplayName("")
        form.displayName.validate()
        #expect(form.displayName.errors[.required] == true)
    }

    @Test("non-empty displayName passes required")
    func nonEmptyNamePassesRequired() {
        let form = UserNameForm()
        form.setDisplayName("Alice")
        form.displayName.validate()
        #expect(form.displayName.errors[.required] == false)
    }

    // MARK: - No whitespace validator

    @Test("whitespace-only name fails noWhiteSpace")
    func whitespaceFails() {
        let form = UserNameForm()
        form.setDisplayName("   ")
        form.displayName.validate()
        #expect(form.displayName.errors[.noWhiteSpace] == true)
    }

    @Test("name with only alphanumeric chars passes noWhiteSpace")
    func alphanumericPassesNoWhiteSpace() {
        let form = UserNameForm()
        form.setDisplayName("Alice1")
        form.displayName.validate()
        #expect(form.displayName.errors[.noWhiteSpace] == false)
    }

    // MARK: - Alphanumeric validator

    @Test("name with special chars fails alphanumeric")
    func specialCharsFail() {
        let form = UserNameForm()
        form.setDisplayName("Alice!")
        form.displayName.validate()
        #expect(form.displayName.errors[.alphanumeric] == true)
    }

    @Test("name with spaces fails alphanumeric")
    func nameWithSpaceFails() {
        let form = UserNameForm()
        form.setDisplayName("Alice Bob")
        form.displayName.validate()
        #expect(form.displayName.errors[.alphanumeric] == true)
    }

    @Test("pure alphanumeric name passes")
    func pureAlphanumericPasses() {
        let form = UserNameForm()
        form.setDisplayName("Alice123")
        form.displayName.validate()
        #expect(form.displayName.errors[.alphanumeric] == false)
    }

    // MARK: - maxLength validator

    @Test("name of exactly 20 chars is valid")
    func maxLengthExact() {
        let form = UserNameForm()
        form.setDisplayName("AliceBob1234567890AB")  // 20 chars
        form.displayName.validate()
        #expect(form.displayName.errors[.maxLength] == false)
    }

    @Test("name of 21 chars fails maxLength")
    func maxLengthExceeded() {
        let form = UserNameForm()
        form.setDisplayName("AliceBob12345678901A")  // 21 chars
        form.displayName.validate()
        #expect(form.displayName.errors[.maxLength] == true)
    }

    // MARK: - userNameUnavailable validator

    @Test("reserved name 'guest' fails userNameUnavailable")
    func guestNameFails() {
        let form = UserNameForm()
        form.setDisplayName("guest")
        form.displayName.validate()
        #expect(form.displayName.errors[.userNameUnavailable] == true)
    }

    @Test("reserved name 'Guest' (uppercase) fails userNameUnavailable")
    func guestUppercaseFails() {
        let form = UserNameForm()
        form.setDisplayName("Guest")
        form.displayName.validate()
        #expect(form.displayName.errors[.userNameUnavailable] == true)
    }

    @Test("non-reserved name passes userNameUnavailable")
    func nonReservedNamePasses() {
        let form = UserNameForm()
        form.setDisplayName("Alice")
        form.displayName.validate()
        #expect(form.displayName.errors[.userNameUnavailable] == false)
    }

    // MARK: - Duplicate validator

    @Test("duplicate name in userList fails duplicate")
    func duplicateNameFails() {
        let form = UserNameForm()
        form.updateUserList([ScaleUser(name: "Alice", token: nil)])
        form.setDisplayName("Alice")
        form.displayName.validate()
        #expect(form.displayName.errors[.duplicate] == true)
    }

    @Test("unique name not in userList passes duplicate")
    func uniqueNamePasses() {
        let form = UserNameForm()
        form.updateUserList([ScaleUser(name: "Alice", token: nil)])
        form.setDisplayName("Bob")
        form.displayName.validate()
        #expect(form.displayName.errors[.duplicate] == false)
    }

    @Test("current user's own name is excluded from duplicate check")
    func currentUserNameExcluded() {
        let form = UserNameForm()
        form.setCurrentUserName("Alice")
        form.updateUserList([ScaleUser(name: "Alice", token: nil)])
        form.setDisplayName("Alice")
        form.displayName.validate()
        #expect(form.displayName.errors[.duplicate] == false)
    }

    // MARK: - Form-level isValid

    @Test("valid name with no duplicates makes form valid")
    func validNameMakesFormValid() {
        let form = UserNameForm()
        form.setDisplayName("Bob123")
        form.displayName.validate()
        #expect(form.isValid == true)
    }

    @Test("invalid name (empty) makes form invalid")
    func invalidNameMakesFormInvalid() {
        let form = UserNameForm()
        form.setDisplayName("")
        form.displayName.validate()
        #expect(form.isValid == false)
    }

    // MARK: - setDisplayName

    @Test("setDisplayName updates value")
    func setDisplayNameUpdates() {
        let form = UserNameForm()
        form.setDisplayName("Charlie")
        #expect(form.displayName.value == "Charlie")
    }

    // MARK: - reset

    @Test("reset clears displayName")
    func resetClearsDisplayName() {
        let form = UserNameForm()
        form.setDisplayName("Alice")
        form.reset()
        #expect(form.displayName.value == "")
    }

    // MARK: - updateUserList

    @Test("updateUserList replaces existing list")
    func updateUserListReplaces() {
        let form = UserNameForm()
        form.updateUserList([ScaleUser(name: "Alice", token: nil)])
        form.updateUserList([ScaleUser(name: "Bob", token: nil), ScaleUser(name: "Carol", token: nil)])
        #expect(form.userList.count == 2)
        #expect(form.userList[0].name == "Bob")
    }

    // MARK: - getError

    @Test("getError returns required message for empty name after touch")
    func getErrorRequired() {
        let form = UserNameForm()
        form.displayName.markAsTouched()
        form.displayName.markAsDirty()
        form.displayName.validate()
        let error = form.getError(for: form.displayName)
        #expect(error != nil)
    }

    @Test("getError returns nil for valid name")
    func getErrorNilForValid() {
        let form = UserNameForm()
        form.setDisplayName("Alice")
        form.displayName.validate()
        let error = form.getError(for: form.displayName)
        #expect(error == nil)
    }
}
