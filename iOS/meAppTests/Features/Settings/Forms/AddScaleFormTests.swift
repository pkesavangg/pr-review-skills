import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct AddScaleFormTests {

    private func makeForm() -> AddDeviceForm { AddDeviceForm() }

    // MARK: - Initial state

    @Test("modelNumber starts empty and pristine")
    func initialState() {
        let form = makeForm()
        #expect(form.modelNumber.value == "")
        #expect(!form.modelNumber.isDirty)
    }

    // MARK: - setModelNumber — numeric filtering

    @Test("setModelNumber filters non-numeric characters")
    func setModelNumberFiltersNonNumeric() {
        let form = makeForm()
        form.setModelNumber("03a4b")
        #expect(form.modelNumber.value == "034")
    }

    @Test("setModelNumber with digits only keeps value")
    func setModelNumberDigitsOnly() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.modelNumber.value == "0340")
    }

    @Test("setModelNumber truncates to 4 digits")
    func setModelNumberTruncatesTo4() {
        let form = makeForm()
        form.setModelNumber("034012345")
        #expect(form.modelNumber.value == "0340")
    }

    @Test("setModelNumber marks control as dirty")
    func setModelNumberMarksDirty() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.modelNumber.isDirty)
    }

    @Test("setModelNumber does not reassign identical filtered value")
    func setModelNumberNoReassignIdentical() {
        let form = makeForm()
        form.modelNumber.value = "0340"
        form.setModelNumber("0340")
        #expect(form.modelNumber.value == "0340")
    }

    @Test("setModelNumber with purely non-numeric input sets empty string")
    func setModelNumberNonNumericOnly() {
        let form = makeForm()
        form.setModelNumber("abcd")
        #expect(form.modelNumber.value == "")
    }

    // MARK: - modelNumberValue accessor

    @Test("modelNumberValue returns current control value")
    func modelNumberValueAccessor() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.modelNumberValue == "0340")
    }

    // MARK: - required / minLength / maxLength validators

    @Test("modelNumber invalid when empty (required)")
    func invalidWhenEmpty() {
        let form = makeForm()
        form.modelNumber.markAsDirty()
        form.modelNumber.validate()
        #expect(!form.modelNumber.isValid)
        #expect(form.modelNumber.errors[.required])
    }

    @Test("modelNumber invalid when fewer than 4 digits (minLength)")
    func invalidWhenTooShort() {
        let form = makeForm()
        form.setModelNumber("034")
        #expect(!form.modelNumber.isValid)
        #expect(form.modelNumber.errors[.minLength])
    }

    @Test("modelNumber invalid when more than 4 digits in raw input but setModelNumber truncates")
    func setModelNumberExactlyFourAfterTruncation() {
        let form = makeForm()
        form.setModelNumber("03401")
        #expect(form.modelNumber.value == "0340")
        #expect(form.modelNumber.isValid || !form.modelNumber.errors[.maxLength])
    }

    // MARK: - skuMatch validator

    @Test("modelNumber valid for known SKU 0340")
    func validKnownSku0340() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.modelNumber.isValid)
    }

    @Test("modelNumber valid for known SKU 0375")
    func validKnownSku0375() {
        let form = makeForm()
        form.setModelNumber("0375")
        #expect(form.modelNumber.isValid)
    }

    @Test("modelNumber invalid for unknown SKU 9999")
    func invalidUnknownSku() {
        let form = makeForm()
        form.setModelNumber("9999")
        #expect(!form.modelNumber.isValid)
        #expect(form.modelNumber.errors[.skuMatch])
    }

    // MARK: - getError for .modelNumber

    @Test("getError returns nil when pristine even if value is empty")
    func getErrorNilWhenPristine() {
        let form = makeForm()
        #expect(form.getError(for: .modelNumber) == nil)
    }

    @Test("getError returns nil for empty value (required hidden until non-empty)")
    func getErrorNilWhenEmptyAfterDirty() {
        let form = makeForm()
        form.setModelNumber("")
        // modelNumberError guard: "guard !modelNumber.value.isEmpty" hides error when empty
        #expect(form.getError(for: .modelNumber) == nil)
    }

    @Test("getError returns modelNumberInvalid for incomplete SKU")
    func getErrorInvalidForShortSku() {
        let form = makeForm()
        form.setModelNumber("034")
        let error = form.getError(for: .modelNumber)
        #expect(error == FormErrorMessages.modelNumberInvalid)
    }

    @Test("getError returns modelNumberInvalid for unknown SKU")
    func getErrorInvalidForUnknownSku() {
        let form = makeForm()
        form.setModelNumber("9999")
        let error = form.getError(for: .modelNumber)
        #expect(error == FormErrorMessages.modelNumberInvalid)
    }

    @Test("getError returns nil for valid SKU")
    func getErrorNilForValidSku() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.getError(for: .modelNumber) == nil)
    }

    @Test("getError returns nil for unrelated FocusField")
    func getErrorNilForOtherField() {
        let form = makeForm()
        form.setModelNumber("9999")
        #expect(form.getError(for: .scaleName) == nil)
    }

    // MARK: - reset

    @Test("reset clears value and marks pristine")
    func resetClearsValue() {
        let form = makeForm()
        form.setModelNumber("0340")
        form.reset()
        #expect(form.modelNumber.value == "")
        #expect(!form.modelNumber.isDirty)
    }

    @Test("reset makes form invalid")
    func resetMakesFormInvalid() {
        let form = makeForm()
        form.setModelNumber("0340")
        form.reset()
        #expect(!form.isValid)
    }

    // MARK: - form-level isValid

    @Test("form is valid with known SKU after setModelNumber")
    func formValidWithKnownSku() {
        let form = makeForm()
        form.setModelNumber("0340")
        #expect(form.isValid)
    }

    @Test("form is invalid with unknown 4-digit SKU")
    func formInvalidWithUnknownSku() {
        let form = makeForm()
        form.setModelNumber("9999")
        #expect(!form.isValid)
    }
}
