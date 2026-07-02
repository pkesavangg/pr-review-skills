import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct WifiPasswordValidationFormTests {

    private func makeForm() -> WifiPasswordValidationForm { WifiPasswordValidationForm() }

    // MARK: - Initial state

    @Test("password starts empty")
    func initialState() {
        let form = makeForm()
        #expect(form.password.value.isEmpty)
        #expect(!form.password.isDirty)
    }

    // MARK: - required validator

    @Test("password invalid when empty and dirty")
    func invalidWhenEmptyAndDirty() {
        let form = makeForm()
        form.password.markAsDirty()
        form.password.validate()
        #expect(!form.password.isValid)
        #expect(form.password.errors[.required])
    }

    // MARK: - minLength(6) validator

    @Test("password invalid when shorter than 6 chars")
    func invalidWhenTooShort() {
        let form = makeForm()
        form.password.value = "abc"
        form.password.validate()
        #expect(!form.password.isValid)
        #expect(form.password.errors[.minLength])
    }

    @Test("password valid at exactly 6 chars")
    func validAtMinLength() {
        let form = makeForm()
        form.password.value = "abcdef"
        form.password.validate()
        #expect(form.password.isValid)
    }

    // MARK: - maxLength(50) validator

    @Test("password valid at exactly 50 chars")
    func validAtMaxLength() {
        let form = makeForm()
        form.password.value = String(repeating: "a", count: 50)
        form.password.validate()
        #expect(form.password.isValid)
    }

    @Test("password invalid when exceeds 50 chars")
    func invalidWhenTooLong() {
        let form = makeForm()
        form.password.value = String(repeating: "a", count: 51)
        form.password.validate()
        #expect(!form.password.isValid)
        #expect(form.password.errors[.maxLength])
    }

    // MARK: - getError behaviour

    @Test("getError returns nil when not dirty")
    func getErrorNilWhenNotDirty() {
        let form = makeForm()
        let error = form.getError(for: form.password)
        #expect(error == nil)
    }

    @Test("getError returns required message when dirty and empty")
    func getErrorRequired() {
        let form = makeForm()
        form.password.value = ""
        form.password.markAsDirty()
        form.password.validate()
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.required)
    }

    @Test("getError returns minLength message when too short")
    func getErrorMinLength() {
        let form = makeForm()
        form.password.value = "abc"
        form.password.markAsDirty()
        form.password.validate()
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.minLength(6))
    }

    @Test("getError returns passwordMaxLength message for password control when too long")
    func getErrorPasswordMaxLength() {
        let form = makeForm()
        form.password.value = String(repeating: "a", count: 51)
        form.password.markAsDirty()
        form.password.validate()
        let error = form.getError(for: form.password)
        #expect(error == FormErrorMessages.passwordMaxLength)
    }

    @Test("getError returns nil when valid and dirty")
    func getErrorNilWhenValid() {
        let form = makeForm()
        form.password.value = "securepassword"
        form.password.markAsDirty()
        form.password.validate()
        let error = form.getError(for: form.password)
        #expect(error == nil)
    }

    // MARK: - formDidChange publisher

    @Test("formDidChange emits when password value changes")
    func formDidChangeEmits() async {
        let form = makeForm()
        var receivedCount = 0
        let cancellable = form.formDidChange.sink { receivedCount += 1 }
        form.password.value = "newpassword"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(receivedCount >= 1)
        _ = cancellable
    }

    // MARK: - valid password

    @Test("password valid with alphanumeric value in range")
    func validPassword() {
        let form = makeForm()
        form.password.value = "MyPassword123"
        form.password.validate()
        #expect(form.password.isValid)
    }
}
