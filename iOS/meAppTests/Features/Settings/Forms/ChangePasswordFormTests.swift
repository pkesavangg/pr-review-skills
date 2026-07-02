import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ChangePasswordFormTests {

    private func makeForm() -> ChangePasswordForm { ChangePasswordForm() }

    // MARK: - Initial state

    @Test("all controls start empty and clean")
    func initialState() {
        let form = makeForm()
        #expect(form.currentPassword.value.isEmpty)
        #expect(form.newPassword.value.isEmpty)
        #expect(form.confirmNewPassword.value.isEmpty)
        #expect(!form.currentPassword.isDirty)
        #expect(!form.newPassword.isDirty)
        #expect(!form.confirmNewPassword.isDirty)
    }

    // MARK: - required validator

    @Test("currentPassword invalid when empty and touched")
    func currentPasswordRequired() {
        let form = makeForm()
        form.currentPassword.markAsTouched()
        form.currentPassword.validate()
        let error = form.getError(for: form.currentPassword)
        #expect(error == FormErrorMessages.required)
    }

    @Test("newPassword invalid when empty and dirty")
    func newPasswordRequired() {
        let form = makeForm()
        form.newPassword.markAsDirty()
        form.newPassword.validate()
        let error = form.getError(for: form.newPassword)
        #expect(error == FormErrorMessages.required)
    }

    @Test("confirmNewPassword invalid when empty and dirty")
    func confirmNewPasswordRequired() {
        let form = makeForm()
        form.confirmNewPassword.markAsDirty()
        form.confirmNewPassword.validate()
        let error = form.getError(for: form.confirmNewPassword)
        #expect(error == FormErrorMessages.required)
    }

    // MARK: - minLength(6) validator

    @Test("currentPassword invalid when shorter than 6 chars")
    func currentPasswordTooShort() {
        let form = makeForm()
        form.currentPassword.value = "abc"
        form.currentPassword.markAsDirty()
        form.currentPassword.validate()
        let error = form.getError(for: form.currentPassword)
        #expect(error == FormErrorMessages.passwordMinLength)
    }

    @Test("newPassword invalid when shorter than 6 chars")
    func newPasswordTooShort() {
        let form = makeForm()
        form.newPassword.value = "abc"
        form.newPassword.markAsDirty()
        form.newPassword.validate()
        let error = form.getError(for: form.newPassword)
        #expect(error == FormErrorMessages.passwordMinLength)
    }

    @Test("password valid at exactly 6 chars")
    func passwordValidAtMinLength() {
        let form = makeForm()
        form.currentPassword.value = "abcdef"
        form.currentPassword.validate()
        #expect(form.currentPassword.isValid)
    }

    // MARK: - maxLength(50) validator

    @Test("password invalid when exceeds 50 chars")
    func passwordTooLong() {
        let form = makeForm()
        form.currentPassword.value = String(repeating: "a", count: 51)
        form.currentPassword.markAsDirty()
        form.currentPassword.validate()
        let error = form.getError(for: form.currentPassword)
        #expect(error == FormErrorMessages.maxLength(50))
    }

    @Test("password valid at exactly 50 chars")
    func passwordValidAtMaxLength() {
        let form = makeForm()
        form.currentPassword.value = String(repeating: "a", count: 50)
        form.currentPassword.validate()
        #expect(form.currentPassword.isValid)
    }

    // MARK: - passwordDifferent form-level error

    @Test("newPassword shows passwordDifferent error when same as current")
    func passwordDifferentError() {
        let form = makeForm()
        form.currentPassword.value = "password123"
        form.currentPassword.markAsDirty()
        form.newPassword.value = "password123"
        form.newPassword.markAsDirty()
        form.confirmNewPassword.value = "password123"
        form.confirmNewPassword.markAsDirty()
        form.validate()
        let error = form.getError(for: form.newPassword)
        #expect(error == FormErrorMessages.newPasswordDifferent)
    }

    @Test("no passwordDifferent error when new differs from current")
    func noPasswordDifferentError() {
        let form = makeForm()
        form.currentPassword.value = "oldPassword"
        form.newPassword.value = "newPassword"
        form.validate()
        #expect(!form.formErrors[.passwordDifferent])
    }

    // MARK: - passwordMatch form-level error

    @Test("confirmNewPassword shows passwordMatch error when mismatch")
    func passwordMatchError() {
        let form = makeForm()
        form.currentPassword.value = "oldPassword"
        form.currentPassword.markAsDirty()
        form.newPassword.value = "newPassword"
        form.newPassword.markAsDirty()
        form.confirmNewPassword.value = "differentPassword"
        form.confirmNewPassword.markAsDirty()
        form.validate()
        let error = form.getError(for: form.confirmNewPassword)
        #expect(error == FormErrorMessages.passwordMatch)
    }

    @Test("no passwordMatch error when confirmPassword matches new")
    func noPasswordMatchError() {
        let form = makeForm()
        form.currentPassword.value = "oldPassword"
        form.newPassword.value = "newPassword"
        form.confirmNewPassword.value = "newPassword"
        form.validate()
        #expect(!form.formErrors[.passwordMatch])
    }

    // MARK: - getError gating on isTouched || isDirty

    @Test("getError returns nil when neither touched nor dirty")
    func getErrorNilWhenClean() {
        let form = makeForm()
        #expect(form.getError(for: form.currentPassword) == nil)
        #expect(form.getError(for: form.newPassword) == nil)
        #expect(form.getError(for: form.confirmNewPassword) == nil)
    }

    @Test("getError shows error when field is only touched (not dirty)")
    func getErrorWhenTouched() {
        let form = makeForm()
        form.currentPassword.markAsTouched()
        form.currentPassword.validate()
        let error = form.getError(for: form.currentPassword)
        #expect(error == FormErrorMessages.required)
    }

    // MARK: - fully valid form

    @Test("form is valid when all controls valid and passwords differ and match")
    func formFullyValid() {
        let form = makeForm()
        form.currentPassword.value = "currentPass1"
        form.newPassword.value = "newPass123"
        form.confirmNewPassword.value = "newPass123"
        form.validate()
        #expect(form.isValid)
    }

    // MARK: - formDidChange

    @Test("formDidChange emits on any field change")
    func formDidChangeEmits() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.newPassword.value = "updated"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }
}
