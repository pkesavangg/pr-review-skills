//
//  LoginFormTests.swift
//  meAppTests
//

import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct LoginFormTests {

    // MARK: - Initial state

    @Test("initial email value is empty")
    func initialEmailValueIsEmpty() {
        let form = LoginForm()
        #expect(form.email.value == "")
    }

    @Test("initial password value is empty")
    func initialPasswordValueIsEmpty() {
        let form = LoginForm()
        #expect(form.password.value == "")
    }

    @Test("form is invalid when no values set")
    func formIsInvalidWithNoValues() {
        let form = LoginForm()
        #expect(!form.isValid)
    }

    // MARK: - isValid

    @Test("form is valid with valid email and password")
    func formIsValidWithValidInputs() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = "password123"
        #expect(form.isValid)
    }

    @Test("form is invalid with empty email")
    func formIsInvalidWithEmptyEmail() {
        let form = LoginForm()
        form.email.value = ""
        form.password.value = "password123"
        #expect(!form.isValid)
    }

    @Test("form is invalid with empty password")
    func formIsInvalidWithEmptyPassword() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = ""
        #expect(!form.isValid)
    }

    @Test("form is invalid with invalid email format")
    func formIsInvalidWithBadEmailFormat() {
        let form = LoginForm()
        form.email.value = "not-an-email"
        form.password.value = "password123"
        #expect(!form.isValid)
    }

    @Test("form is invalid with short password (5 chars)")
    func formIsInvalidWithShortPassword() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = "abc12"
        #expect(!form.isValid)
    }

    @Test("form is valid with minimum password length (6 chars)")
    func formIsValidWithMinimumPassword() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = "abc123"
        #expect(form.isValid)
    }

    @Test("form is invalid with password exceeding 50 chars")
    func formIsInvalidWithLongPassword() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = String(repeating: "a", count: 51)
        #expect(!form.isValid)
    }

    @Test("form is valid with exactly 50-char password")
    func formIsValidWithMaxPassword() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = String(repeating: "a", count: 50)
        #expect(form.isValid)
    }

    @Test("form is invalid with email exceeding 100 chars")
    func formIsInvalidWithLongEmail() {
        let form = LoginForm()
        let localPart = String(repeating: "a", count: 93)
        form.email.value = "\(localPart)@b.com"  // 101 chars total
        form.password.value = "password123"
        #expect(!form.isValid)
    }

    @Test("form is valid with exactly 100-char email")
    func formIsValidWithMaxEmail() {
        let form = LoginForm()
        let localPart = String(repeating: "a", count: 92)
        form.email.value = "\(localPart)@b.com"  // exactly 100 chars
        form.password.value = "password123"
        #expect(form.isValid)
    }

    // MARK: - getError: email field

    @Test("getError returns nil for untouched pristine email")
    func emailErrorIsNilBeforeTouched() {
        let form = LoginForm()
        #expect(form.getError(for: form.email) == nil)
    }

    @Test("getError returns required error for empty dirty email")
    func emailErrorIsRequiredWhenEmptyAndDirty() {
        let form = LoginForm()
        form.email.markAsDirty()
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.leaveBlank)
    }

    @Test("getError returns email format error for invalid email when dirty")
    func emailErrorIsEmailFormatWhenInvalidAndDirty() {
        let form = LoginForm()
        form.email.value = "not-an-email"
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.email)
    }

    @Test("getError returns emailMaxLength error when email exceeds 100 chars")
    func emailErrorIsMaxLengthWhenTooLong() {
        let form = LoginForm()
        let localPart = String(repeating: "a", count: 93)
        form.email.value = "\(localPart)@b.com"  // 101 chars
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.emailMaxLength)
    }

    @Test("getError returns nil for valid email when dirty")
    func emailErrorIsNilForValidEmail() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        let error = form.getError(for: form.email)
        #expect(error == nil)
    }

    @Test("getError returns nil for untouched email even when invalid")
    func emailErrorIsNilIfNotTouchedOrDirty() {
        let form = LoginForm()
        // email is "" (invalid) but we haven't touched or dirtied it
        // Since value starts as "" with no interaction, getError should return nil
        form.email.resetInteractionState()
        let error = form.getError(for: form.email)
        #expect(error == nil)
    }

    // MARK: - getError: password field

    @Test("getError returns nil for untouched pristine password")
    func passwordErrorIsNilBeforeTouched() {
        let form = LoginForm()
        #expect(form.getError(for: form.password) == nil)
    }

    @Test("getError returns required error for empty dirty password")
    func passwordErrorIsRequiredWhenEmptyAndDirty() {
        let form = LoginForm()
        form.password.markAsDirty()
        form.password.validate()
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.leaveBlank)
    }

    @Test("getError returns passwordMinLength error for short password")
    func passwordErrorIsMinLengthWhenTooShort() {
        let form = LoginForm()
        form.password.value = "abc12"  // 5 chars - marks dirty, validates
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.passwordMinLength)
    }

    @Test("getError returns passwordMaxLength error for long password")
    func passwordErrorIsMaxLengthWhenTooLong() {
        let form = LoginForm()
        form.password.value = String(repeating: "a", count: 51)
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.passwordMaxLength)
    }

    @Test("getError returns nil for valid password when dirty")
    func passwordErrorIsNilForValidPassword() {
        let form = LoginForm()
        form.password.value = "validpass"
        let error = form.getError(for: form.password)
        #expect(error == nil)
    }

    // MARK: - formDidChange publishes

    @Test("formDidChange emits when email value changes")
    func formDidChangeEmitsOnEmailChange() async throws {
        let form = LoginForm()
        var receivedChange = false
        let cancellable = form.formDidChange.sink { receivedChange = true }
        defer { cancellable.cancel() }

        form.email.value = "new@example.com"

        try await Task.sleep(for: .milliseconds(50))
        #expect(receivedChange)
    }

    @Test("formDidChange emits when password value changes")
    func formDidChangeEmitsOnPasswordChange() async throws {
        let form = LoginForm()
        var receivedChange = false
        let cancellable = form.formDidChange.sink { receivedChange = true }
        defer { cancellable.cancel() }

        form.password.value = "newpassword"

        try await Task.sleep(for: .milliseconds(50))
        #expect(receivedChange)
    }
}
