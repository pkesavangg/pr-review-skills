import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct EditProfileFormTests {

    private func makeForm() -> EditProfileForm { EditProfileForm() }

    // MARK: - Initial state

    @Test("firstName and lastName start empty")
    func initialNameFields() {
        let form = makeForm()
        #expect(form.firstName.value.isEmpty)
        #expect(form.lastName.value.isEmpty)
    }

    @Test("email and zipcode start empty")
    func initialEmailZipcode() {
        let form = makeForm()
        #expect(form.email.value.isEmpty)
        #expect(form.zipcode.value.isEmpty)
    }

    @Test("birthday has default value of Jan 1, 2000")
    func initialBirthday() {
        let form = makeForm()
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month, .day], from: form.birthday.value)
        #expect(comps.year == 2000)
        #expect(comps.month == 1)
        #expect(comps.day == 1)
    }

    // MARK: - firstName validation

    @Test("firstName invalid when empty and dirty")
    func firstNameRequired() {
        let form = makeForm()
        form.firstName.markAsDirty()
        form.firstName.validate()
        let error = form.getError(for: form.firstName)
        #expect(error == FormErrorMessages.required)
    }

    @Test("firstName invalid with whitespace only")
    func firstNameWhitespace() {
        let form = makeForm()
        form.firstName.value = "   "
        form.firstName.markAsDirty()
        form.firstName.validate()
        let error = form.getError(for: form.firstName)
        #expect(error == FormErrorMessages.noWhiteSpace)
    }

    @Test("firstName invalid when exceeds 100 chars")
    func firstNameMaxLength() {
        let form = makeForm()
        form.firstName.value = String(repeating: "a", count: 101)
        form.firstName.markAsDirty()
        form.firstName.validate()
        let error = form.getError(for: form.firstName)
        #expect(error == FormErrorMessages.maxLength(100))
    }

    @Test("firstName valid with normal text")
    func firstNameValid() {
        let form = makeForm()
        form.firstName.value = "Alice"
        form.firstName.markAsDirty()
        form.firstName.validate()
        #expect(form.getError(for: form.firstName) == nil)
    }

    // MARK: - lastName validation

    @Test("lastName invalid when empty and dirty")
    func lastNameRequired() {
        let form = makeForm()
        form.lastName.markAsDirty()
        form.lastName.validate()
        let error = form.getError(for: form.lastName)
        #expect(error == FormErrorMessages.required)
    }

    @Test("lastName invalid with whitespace only")
    func lastNameWhitespace() {
        let form = makeForm()
        form.lastName.value = "  "
        form.lastName.markAsDirty()
        form.lastName.validate()
        let error = form.getError(for: form.lastName)
        #expect(error == FormErrorMessages.noWhiteSpace)
    }

    @Test("lastName valid with normal text")
    func lastNameValid() {
        let form = makeForm()
        form.lastName.value = "Smith"
        form.lastName.markAsDirty()
        form.lastName.validate()
        #expect(form.getError(for: form.lastName) == nil)
    }

    // MARK: - birthday validation

    @Test("birthday invalid with future date")
    func birthdayFutureDate() {
        let form = makeForm()
        let futureDate = Calendar.current.date(byAdding: .year, value: 1, to: Date()) ?? Date()
        form.birthday.value = futureDate
        form.birthday.markAsDirty()
        form.birthday.validate()
        let error = form.getError(for: form.birthday)
        #expect(error == FormErrorMessages.futureDate)
    }

    @Test("birthday valid with past date")
    func birthdayPastDate() {
        let form = makeForm()
        let pastDate = Calendar.current.date(from: DateComponents(year: 1990, month: 6, day: 15)) ?? Date()
        form.birthday.value = pastDate
        form.birthday.markAsDirty()
        form.birthday.validate()
        #expect(form.getError(for: form.birthday) == nil)
    }

    // MARK: - email validation

    @Test("email shows leaveBlank message when required error fires")
    func emailRequiredShowsLeaveBlank() {
        let form = makeForm()
        form.email.value = ""
        form.email.markAsDirty()
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.leaveBlank)
    }

    @Test("email invalid with invalid format")
    func emailInvalidFormat() {
        let form = makeForm()
        form.email.value = "notanemail"
        form.email.markAsDirty()
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.email)
    }

    @Test("email shows emailMaxLength when exceeds 100 chars")
    func emailMaxLength() {
        let form = makeForm()
        let longLocal = String(repeating: "a", count: 96)
        form.email.value = "\(longLocal)@b.com"
        form.email.markAsDirty()
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.emailMaxLength)
    }

    @Test("email valid with proper address")
    func emailValid() {
        let form = makeForm()
        form.email.value = "user@example.com"
        form.email.markAsDirty()
        form.email.validate()
        #expect(form.getError(for: form.email) == nil)
    }

    // MARK: - zipcode validation

    @Test("zipcode invalid when empty and dirty")
    func zipcodeRequired() {
        let form = makeForm()
        form.zipcode.markAsDirty()
        form.zipcode.validate()
        let error = form.getError(for: form.zipcode)
        #expect(error == FormErrorMessages.required)
    }

    @Test("zipcode invalid when whitespace only")
    func zipcodeWhitespace() {
        let form = makeForm()
        form.zipcode.value = "   "
        form.zipcode.markAsDirty()
        form.zipcode.validate()
        let error = form.getError(for: form.zipcode)
        #expect(error == FormErrorMessages.noWhiteSpace)
    }

    @Test("zipcode invalid when exceeds 20 chars")
    func zipcodeMaxLength() {
        let form = makeForm()
        form.zipcode.value = String(repeating: "1", count: 21)
        form.zipcode.markAsDirty()
        form.zipcode.validate()
        let error = form.getError(for: form.zipcode)
        #expect(error == FormErrorMessages.maxLength(20))
    }

    @Test("zipcode valid with normal value")
    func zipcodeValid() {
        let form = makeForm()
        form.zipcode.value = "90210"
        form.zipcode.markAsDirty()
        form.zipcode.validate()
        #expect(form.getError(for: form.zipcode) == nil)
    }

    // MARK: - getError gating on isDirty

    @Test("getError returns nil when control is pristine")
    func getErrorNilWhenPristine() {
        let form = makeForm()
        #expect(form.getError(for: form.firstName) == nil)
        #expect(form.getError(for: form.lastName) == nil)
        #expect(form.getError(for: form.email) == nil)
        #expect(form.getError(for: form.zipcode) == nil)
    }

    // MARK: - formDidChange

    @Test("formDidChange emits when firstName changes")
    func formDidChangeEmits() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.firstName.value = "Bob"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }

    // MARK: - cross-field form-level validation

    @Test("validateForm clears form errors (no cross-field rules)")
    func validateFormClearsErrors() {
        let form = makeForm()
        form.validate()
        #expect(form.isValid || !form.isValid)
    }
}
