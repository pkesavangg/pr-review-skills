import Combine
@testable import meApp
import Testing

/// Tests for the `ObservableForm` base class behavior.
///
/// `ObservableForm` is an abstract-ish `open class`, so its logic is exercised
/// through concrete subclasses:
/// - `BasicProfileForm` (defined alongside `ObservableForm`) covers the
///   form-level validation path (`validateForm`, `updateFormErrors`, `formErrors`)
///   and the per-control error mapping in `getError`.
/// - `LoginForm` (a lightweight, dependency-free subclass) covers control
///   collection, validity aggregation, and dirty/pristine state.
@Suite(.serialized)
struct ObservableFormTests {
    // MARK: - Control collection & initial state

    @Test("form collects its controls and starts pristine")
    func initialStateIsPristine() {
        let form = LoginForm()

        #expect(form.isPristine == true)
        #expect(form.isDirty == false)
        // Required fields are empty, so the form is not yet valid.
        #expect(form.isValid == false)
        #expect(form.isInvalid == true)
    }

    @Test("form aggregates control validity into isValid")
    func isValidAggregatesControls() {
        let form = LoginForm()

        form.email.value = "user@example.com"
        form.password.value = "password123"
        #expect(form.isValid == true)
        #expect(form.isInvalid == false)

        // Break one control -> whole form invalid.
        form.password.value = "123"
        #expect(form.isValid == false)
    }

    @Test("changing a control marks the form dirty")
    func changingControlMarksFormDirty() {
        let form = LoginForm()
        #expect(form.isDirty == false)

        form.email.value = "user@example.com"
        #expect(form.isDirty == true)
        #expect(form.isPristine == false)
    }

    @Test("marking all controls pristine returns the form to pristine")
    func markingControlsPristine() {
        let form = LoginForm()
        form.email.value = "user@example.com"
        form.password.value = "password123"
        #expect(form.isDirty == true)

        form.email.markAsPristine()
        form.password.markAsPristine()
        #expect(form.isPristine == true)
        #expect(form.isDirty == false)
    }

    @Test("validate() propagates to every control")
    func validatePropagatesToControls() {
        let form = LoginForm()
        // Manually dirty without validating individually, then validate the form.
        form.email.markAsDirty()
        form.password.markAsDirty()

        form.validate()

        #expect(form.email.errors[.required] == true)
        #expect(form.password.errors[.required] == true)
        #expect(form.isValid == false)
    }

    @Test("form forwards control objectWillChange emissions")
    func forwardsObjectWillChange() {
        let form = LoginForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.objectWillChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.email.value = "user@example.com"

        #expect(emissions >= 1)
    }

    // MARK: - Form-level validation (formErrors / updateFormErrors / validateForm)

    @Test("mismatched passwords produce a form-level error")
    func formLevelPasswordMismatch() {
        let form = BasicProfileForm()

        form.password.value = "password123"
        form.confirmPassword.value = "different456"

        #expect(form.formErrors[.passwordMatch] == true)
        #expect(form.isValid == false)
    }

    @Test("matching passwords clear the form-level error")
    func formLevelPasswordMatchClears() {
        let form = BasicProfileForm()

        form.password.value = "password123"
        form.confirmPassword.value = "wrong"
        #expect(form.formErrors[.passwordMatch] == true)

        form.confirmPassword.value = "password123"
        #expect(form.formErrors[.passwordMatch] == false)
    }

    @Test("updateFormValues copies profile data into controls")
    func updateFormValuesCopiesData() {
        let form = BasicProfileForm()
        let profile = ProfileData(
            name: "Jane",
            email: "jane@example.com",
            age: 30,
            rememberMe: true,
            username: "jane",
            dob: Date(timeIntervalSince1970: 0),
            password: "password123",
            confirmPassword: "password123"
        )

        form.updateFormValues(with: profile)

        #expect(form.name.value == "Jane")
        #expect(form.email.value == "jane@example.com")
        #expect(form.age.value == 30)
        #expect(form.rememberMe.value == true)
        #expect(form.username.value == "jane")
        #expect(form.password.value == "password123")
        #expect(form.confirmPassword.value == "password123")
        // Matching passwords -> no form-level mismatch error.
        #expect(form.formErrors[.passwordMatch] == false)
    }

    // MARK: - getError mapping (per-control validation messages)

    @Test("getError returns required message for empty required field")
    func getErrorRequired() {
        let form = BasicProfileForm()
        form.name.value = "a"   // dirty + valid
        form.name.value = ""    // dirty + required error

        #expect(form.getError(for: form.name) == SampleFormErrorMessages.required)
    }

    @Test("getError returns email message for malformed email")
    func getErrorEmail() {
        let form = BasicProfileForm()
        form.email.value = "not-an-email"

        #expect(form.getError(for: form.email) == SampleFormErrorMessages.email)
    }

    @Test("getError returns maxLength message when too long")
    func getErrorMaxLength() {
        let form = BasicProfileForm()
        form.name.value = String(repeating: "x", count: 11) // maxLength is 10

        #expect(form.getError(for: form.name) == SampleFormErrorMessages.maxLength(10))
    }

    @Test("getError returns minLength message for short password")
    func getErrorMinLength() {
        let form = BasicProfileForm()
        form.password.value = "123" // minLength is 6

        #expect(form.getError(for: form.password) == SampleFormErrorMessages.minLength(6))
    }

    @Test("getError returns min message for out-of-range age")
    func getErrorMin() {
        let form = BasicProfileForm()
        form.age.value = 8 // min is 10

        #expect(form.getError(for: form.age) == SampleFormErrorMessages.min(10))
    }

    @Test("getError returns noWhiteSpace message for whitespace-only username")
    func getErrorNoWhiteSpace() {
        let form = BasicProfileForm()
        form.username.value = "   " // non-empty but whitespace only

        #expect(form.getError(for: form.username) == SampleFormErrorMessages.noWhiteSpace)
    }

    @Test("getError returns futureDate message for a future dob")
    func getErrorFutureDate() {
        let form = BasicProfileForm()
        form.dob.value = Date(timeIntervalSinceNow: 60 * 60 * 24 * 365)

        #expect(form.getError(for: form.dob) == SampleFormErrorMessages.futureDate)
    }

    @Test("getError returns requiredTrue message for unchecked toggle")
    func getErrorRequiredTrue() {
        let form = BasicProfileForm()
        form.rememberMe.value = true  // dirty + valid
        form.rememberMe.value = false // dirty + requiredTrue error

        #expect(form.getError(for: form.rememberMe) == SampleFormErrorMessages.requiredTrue)
    }

    @Test("getError returns passwordMatch message on confirmPassword")
    func getErrorPasswordMatch() {
        let form = BasicProfileForm()
        form.password.value = "password123"
        form.confirmPassword.value = "different456"

        #expect(form.getError(for: form.confirmPassword) == SampleFormErrorMessages.passwordMatch)
    }

    @Test("getError returns nil for a pristine control")
    func getErrorNilWhenPristine() {
        let form = BasicProfileForm()
        // name is invalid (empty required) but pristine -> no error surfaced.
        #expect(form.getError(for: form.name) == nil)
    }
}
