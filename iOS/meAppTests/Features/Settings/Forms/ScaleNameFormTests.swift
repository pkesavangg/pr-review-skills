import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ScaleNameFormTests {

    private func makeForm() -> DeviceNameForm { DeviceNameForm() }

    // MARK: - Initial state

    @Test("scaleName starts empty and pristine")
    func initialState() {
        let form = makeForm()
        #expect(form.scaleName.value.isEmpty)
        #expect(!form.scaleName.isDirty)
        #expect(!form.isValid)
    }

    // MARK: - required validator

    @Test("scaleName invalid when empty after dirty")
    func invalidWhenEmpty() {
        let form = makeForm()
        form.scaleName.markAsDirty()
        form.scaleName.validate()
        #expect(!form.scaleName.isValid)
        #expect(form.scaleName.errors[.required])
    }

    @Test("scaleName valid with normal text")
    func validWithNormalText() {
        let form = makeForm()
        form.scaleName.value = "My Scale"
        form.scaleName.validate()
        #expect(form.scaleName.isValid)
    }

    // MARK: - noWhiteSpace validator

    @Test("scaleName invalid with whitespace-only value")
    func invalidWithWhitespaceOnly() {
        let form = makeForm()
        form.scaleName.value = "   "
        form.scaleName.validate()
        #expect(!form.scaleName.isValid)
        #expect(form.scaleName.errors[.noWhiteSpace])
    }

    // MARK: - maxLength(100) validator

    @Test("scaleName valid at exactly 100 characters")
    func validAtMaxLength() {
        let form = makeForm()
        form.scaleName.value = String(repeating: "a", count: 100)
        form.scaleName.validate()
        #expect(form.scaleName.isValid)
    }

    @Test("scaleName invalid when exceeds 100 characters")
    func invalidWhenExceedsMaxLength() {
        let form = makeForm()
        form.scaleName.value = String(repeating: "a", count: 101)
        form.scaleName.validate()
        #expect(!form.scaleName.isValid)
        #expect(form.scaleName.errors[.maxLength])
    }

    // MARK: - setDeviceName

    @Test("setDeviceName updates value and marks dirty")
    func setDeviceNameUpdatesDirty() {
        let form = makeForm()
        form.setDeviceName("Bathroom Scale")
        #expect(form.scaleName.isDirty)
        #expect(form.scaleName.value == "Bathroom Scale")
    }

    @Test("setDeviceName validates after setting value")
    func setDeviceNameValidates() {
        let form = makeForm()
        form.setDeviceName("Valid Name")
        #expect(form.isValid)
    }

    @Test("setDeviceName with empty marks invalid")
    func setDeviceNameEmptyIsInvalid() {
        let form = makeForm()
        form.setDeviceName("")
        #expect(!form.isValid)
    }

    @Test("setDeviceName does not re-assign identical value")
    func setDeviceNameIdenticalValueNotReassigned() {
        let form = makeForm()
        form.scaleName.value = "Test"
        form.setDeviceName("Test")
        #expect(form.scaleName.value == "Test")
        #expect(form.scaleName.isDirty)
    }

    // MARK: - scaleNameValue accessor

    @Test("scaleNameValue returns current control value")
    func scaleNameValueAccessor() {
        let form = makeForm()
        form.scaleName.value = "Kitchen Scale"
        #expect(form.scaleNameValue == "Kitchen Scale")
    }

    // MARK: - reset

    @Test("reset clears value and marks pristine")
    func resetClearsValue() {
        let form = makeForm()
        form.setDeviceName("Name")
        form.reset()
        #expect(form.scaleName.value.isEmpty)
        #expect(!form.scaleName.isDirty)
    }

    @Test("reset makes form invalid")
    func resetMakesFormInvalid() {
        let form = makeForm()
        form.setDeviceName("Name")
        form.reset()
        #expect(!form.isValid)
    }

    // MARK: - getError

    @Test("getError returns nil when pristine")
    func getErrorNilWhenPristine() {
        let form = makeForm()
        #expect(form.getError(for: .scaleName) == nil)
    }

    @Test("getError returns required message when dirty and empty")
    func getErrorRequiredWhenDirtyEmpty() {
        let form = makeForm()
        form.setDeviceName("")
        let error = form.getError(for: .scaleName)
        #expect(error == FormErrorMessages.required)
    }

    @Test("getError returns noWhiteSpace message for whitespace input")
    func getErrorNoWhiteSpace() {
        let form = makeForm()
        form.setDeviceName("  ")
        let error = form.getError(for: .scaleName)
        #expect(error == FormErrorMessages.noWhiteSpace)
    }

    @Test("getError returns maxLength message when too long")
    func getErrorMaxLength() {
        let form = makeForm()
        form.setDeviceName(String(repeating: "x", count: 101))
        let error = form.getError(for: .scaleName)
        #expect(error == FormErrorMessages.maxLength(100))
    }

    @Test("getError returns nil for unrelated FocusField")
    func getErrorNilForOtherField() {
        let form = makeForm()
        form.setDeviceName("")
        #expect(form.getError(for: .firstName) == nil)
    }

    @Test("getError returns nil when valid")
    func getErrorNilWhenValid() {
        let form = makeForm()
        form.setDeviceName("My Scale")
        #expect(form.getError(for: .scaleName) == nil)
    }

    // MARK: - form-level isValid

    @Test("form is valid when scaleName is non-empty and no-whitespace")
    func formIsValid() {
        let form = makeForm()
        form.setDeviceName("Body Scale")
        #expect(form.isValid)
    }

    @Test("form is invalid with whitespace-only name")
    func formInvalidWhitespace() {
        let form = makeForm()
        form.setDeviceName("   ")
        #expect(!form.isValid)
    }
}
