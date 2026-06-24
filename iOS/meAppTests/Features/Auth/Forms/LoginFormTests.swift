import Combine
import Testing
@testable import meApp

struct LoginFormTests {
    @Test("initial state")
    func initialState() {
        let form = LoginForm()

        #expect(form.email.value == "")
        #expect(form.password.value == "")
        #expect(form.isValid == false)
        #expect(form.email.isTouched == false)
        #expect(form.password.isTouched == false)
        #expect(form.isPristine == true)
    }

    @Test("email required error")
    func emailRequired() {
        let form = LoginForm()

        form.email.markAsDirty()
        form.email.validate()

        #expect(form.email.errors[.required] == true)
        #expect(form.email.isValid == false)
        #expect(form.getError(for: form.email) != nil)
    }

    @Test("email format validation")
    func emailFormatValidation() {
        let form = LoginForm()

        form.email.value = "invalid-email"

        #expect(form.email.errors[.email] == true)
        #expect(form.email.isValid == false)

        form.email.value = "test@example.com"

        #expect(form.email.errors[.email] == false)
        #expect(form.email.errors[.required] == false)
        #expect(form.email.isValid == true)
    }

    @Test("email max length")
    func emailMaxLength() {
        let form = LoginForm()

        let tooLongEmail = String(repeating: "a", count: 90) + "@example.com"
        form.email.value = tooLongEmail

        #expect(form.email.errors[.maxLength] == true)
        #expect(form.email.isValid == false)

        let boundaryEmail = String(repeating: "a", count: 88) + "@example.com"
        form.email.value = boundaryEmail

        #expect(form.email.errors[.maxLength] == false)
    }

    @Test("password length validation")
    func passwordLengthValidation() {
        let form = LoginForm()

        form.password.value = "12345"
        #expect(form.password.errors[.minLength] == true)
        #expect(form.password.isValid == false)

        form.password.value = "123456"
        #expect(form.password.errors[.minLength] == false)

        form.password.value = String(repeating: "a", count: 51)
        #expect(form.password.errors[.maxLength] == true)

        form.password.value = String(repeating: "a", count: 50)
        #expect(form.password.errors[.maxLength] == false)
    }

    @Test("form validity combinations")
    func formValidityCombinations() {
        let form = LoginForm()

        form.email.value = "test@example.com"
        form.password.value = "password123"
        #expect(form.isValid == true)

        form.email.value = "invalid-email"
        #expect(form.isValid == false)

        form.email.value = "test@example.com"
        form.password.value = "12345"
        #expect(form.isValid == false)
    }

    @Test("error visibility requires touch or dirty")
    func errorVisibilityRules() {
        let form = LoginForm()

        form.email.value = ""
        form.password.value = ""
        #expect(form.getError(for: form.email) == nil)
        #expect(form.getError(for: form.password) == nil)

        form.email.markAsTouched()
        form.password.markAsTouched()
        #expect(form.getError(for: form.email) != nil)
        #expect(form.getError(for: form.password) != nil)
    }

    @Test("form dirty and pristine state")
    func formDirtyAndPristineState() {
        let form = LoginForm()

        #expect(form.isPristine == true)
        #expect(form.isDirty == false)

        form.email.value = "test@example.com"
        #expect(form.email.isDirty == true)
        #expect(form.isDirty == true)

        form.email.markAsPristine()
        #expect(form.email.isPristine == true)
        #expect(form.email.isDirty == false)
    }

    @Test("common valid and invalid email formats")
    func emailExamples() {
        let validEmails = [
            "test@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk",
            "test123@test-domain.com"
        ]

        for email in validEmails {
            let form = LoginForm()
            form.email.value = email
            #expect(form.email.isValid == true, "Email \(email) should be valid")
        }

        let invalidEmails = [
            "invalid",
            "@example.com",
            "test@",
            "test @example.com",
            "test@example"
        ]

        for email in invalidEmails {
            let form = LoginForm()
            form.email.value = email
            #expect(form.email.isValid == false, "Email \(email) should be invalid")
        }
    }

    @Test("password uses password-specific error messages")
    func passwordSpecificErrorMessages() {
        let form = LoginForm()
        form.password.markAsTouched()

        form.password.value = "12345"
        #expect(form.getError(for: form.password) == LoginFormTestText.passwordMinLength)

        form.password.value = String(repeating: "a", count: 51)
        #expect(form.getError(for: form.password) == LoginFormTestText.passwordMaxLength)
    }

    @Test("generic min and max length messages for non-email/password controls")
    func genericLengthErrorMessages() {
        let form = LoginForm()
        let control = FormControl("", validators: [.minLength(3), .maxLength(5)])
        control.markAsTouched()

        control.value = "ab"
        #expect(form.getError(for: control) == LoginFormTestText.minLength(3))

        control.value = "abcdef"
        #expect(form.getError(for: control) == LoginFormTestText.maxLength(5))
    }

    @Test("getError returns nil when touched but valid")
    func getErrorReturnsNilWhenValid() {
        let form = LoginForm()
        form.email.value = "valid@example.com"
        form.email.markAsTouched()

        #expect(form.getError(for: form.email) == nil)
    }

    @Test("formDidChange emits when email and password change")
    func formDidChangePublishes() {
        let form = LoginForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.email.value = "one@example.com"
        form.password.value = "secret123"

        #expect(emissions >= 2)
    }
}

private enum LoginFormTestText {
    static let passwordMinLength = "password must be 6 characters long"
    static let passwordMaxLength = "password should not exceed 50 characters"
    static func minLength(_ length: Int) -> String { "minimum value should be \(length)" }
    static func maxLength(_ length: Int) -> String { "maximum value should be \(length)" }
}
