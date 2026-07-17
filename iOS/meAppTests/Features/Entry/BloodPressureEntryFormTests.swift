import Combine
import Foundation
@testable import meApp
import Testing

struct BloodPressureEntryFormTests {
    // MARK: - Initial State

    @Test("initial state has empty values and form is invalid")
    func initialState() {
        let form = BloodPressureEntryForm()

        #expect(form.systolic.value.isEmpty)
        #expect(form.diastolic.value.isEmpty)
        #expect(form.pulse.value.isEmpty)
        #expect(form.notes.value.isEmpty)
        #expect(form.isValid == false)
        #expect(form.isPristine == true)
    }

    // MARK: - Required Field Errors

    @Test("systolic shows required error when dirty and empty")
    func systolicRequiredError() {
        let form = BloodPressureEntryForm()

        form.systolic.markAsDirty()
        form.systolic.validate()

        #expect(form.systolic.errors[.required] == true)
        #expect(form.systolic.isValid == false)
        #expect(form.getError(for: form.systolic) == BPFormTestText.required)
    }

    @Test("diastolic shows required error when dirty and empty")
    func diastolicRequiredError() {
        let form = BloodPressureEntryForm()

        form.diastolic.markAsDirty()
        form.diastolic.validate()

        #expect(form.diastolic.errors[.required] == true)
        #expect(form.diastolic.isValid == false)
        #expect(form.getError(for: form.diastolic) == BPFormTestText.required)
    }

    @Test("pulse shows required error when dirty and empty")
    func pulseRequiredError() {
        let form = BloodPressureEntryForm()

        form.pulse.markAsDirty()
        form.pulse.validate()

        #expect(form.pulse.errors[.required] == true)
        #expect(form.pulse.isValid == false)
        #expect(form.getError(for: form.pulse) == BPFormTestText.required)
    }

    // MARK: - Max Limit Errors

    @Test("systolic shows max limit error when value exceeds 500")
    func systolicMaxLimitError() {
        let form = BloodPressureEntryForm()

        form.systolic.value = "501"

        #expect(form.systolic.errors[.maxLimit] == true)
        #expect(form.systolic.isValid == false)
        #expect(form.getError(for: form.systolic) == BPFormTestText.maxLimit)
    }

    @Test("diastolic shows max limit error when value exceeds 500")
    func diastolicMaxLimitError() {
        let form = BloodPressureEntryForm()

        form.diastolic.value = "600"

        #expect(form.diastolic.errors[.maxLimit] == true)
        #expect(form.diastolic.isValid == false)
        #expect(form.getError(for: form.diastolic) == BPFormTestText.maxLimit)
    }

    // MARK: - Systolic Warnings

    @Test("systolic warns when diastolic is higher and systolic in range")
    func systolicReversedWarning() {
        let form = BloodPressureEntryForm()

        form.systolic.value = "80"
        form.diastolic.value = "120"

        #expect(form.getError(for: form.systolic) == nil)
        #expect(form.getWarning(for: form.systolic) == BPFormTestText.systolicReversed)
    }

    @Test("systolic warns when value is outside typical range")
    func systolicOutOfRangeWarning() {
        let form = BloodPressureEntryForm()

        form.systolic.value = "50"
        #expect(form.getWarning(for: form.systolic) == BPFormTestText.typicalRange(60, 250))

        form.systolic.value = "260"
        #expect(form.getWarning(for: form.systolic) == BPFormTestText.typicalRange(60, 250))
    }

    // MARK: - Diastolic Warnings

    @Test("diastolic warns reversed when systolic is lower than diastolic")
    func diastolicReversedWithSystolic() {
        let form = BloodPressureEntryForm()

        form.systolic.value = "80"
        form.diastolic.value = "100"

        #expect(form.getError(for: form.diastolic) == nil)
        #expect(form.getWarning(for: form.diastolic) == BPFormTestText.diastolicReversed)
    }

    @Test("diastolic warns reversed when systolic is empty")
    func diastolicReversedWithEmptySystolic() {
        let form = BloodPressureEntryForm()

        form.diastolic.value = "80"

        #expect(form.getError(for: form.diastolic) == nil)
        #expect(form.getWarning(for: form.diastolic) == BPFormTestText.diastolicReversed)
    }

    @Test("diastolic warns when value is outside typical range")
    func diastolicOutOfRangeWarning() {
        let form = BloodPressureEntryForm()

        form.diastolic.value = "25"
        #expect(form.getWarning(for: form.diastolic) == BPFormTestText.typicalRange(30, 150))

        form.diastolic.value = "160"
        #expect(form.getWarning(for: form.diastolic) == BPFormTestText.typicalRange(30, 150))
    }

    // MARK: - Pulse Warnings

    @Test("pulse warns when value is outside typical range")
    func pulseOutOfRangeWarning() {
        let form = BloodPressureEntryForm()

        form.pulse.value = "15"
        #expect(form.getWarning(for: form.pulse) == BPFormTestText.typicalRange(20, 200))

        form.pulse.value = "210"
        #expect(form.getWarning(for: form.pulse) == BPFormTestText.typicalRange(20, 200))
    }

    // MARK: - Warning Suppression

    @Test("warning is suppressed when field has a blocking error")
    func noWarningWhenBlockingError() {
        let form = BloodPressureEntryForm()

        form.systolic.value = "501"

        #expect(form.getError(for: form.systolic) != nil)
        #expect(form.getWarning(for: form.systolic) == nil)
    }

    // MARK: - Date Validation

    @Test("date shows future date error when set to tomorrow")
    func dateFutureDateError() throws {
        let form = BloodPressureEntryForm()

        let tomorrow = try #require(Calendar.current.date(byAdding: .day, value: 1, to: Date()))
        form.date.value = tomorrow

        #expect(form.date.errors[.futureDate] == true)
        #expect(form.getError(for: form.date) == BPFormTestText.futureDate)

        form.date.value = Date()
        #expect(form.date.errors[.futureDate] == false)
        #expect(form.getError(for: form.date) == nil)
    }

    // MARK: - Form Validity & Publisher

    @Test("form is valid when all required fields filled and formDidChange emits")
    func formValidityAndFormDidChange() {
        let form = BloodPressureEntryForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.systolic.value = "120"
        form.diastolic.value = "80"
        form.pulse.value = "72"

        #expect(form.isValid == true)
        #expect(emissions >= 3)
    }
}

// MARK: - Test String Constants

private enum BPFormTestText {
    static let required = "this field is required"
    static let maxLimit = "this value cannot be over 500."
    static let futureDate = "future dates not accepted"
    static let systolicReversed = "systolic should be higher than diastolic"
    static let diastolicReversed = "diastolic should be lower than systolic"
    static func typicalRange(_ min: Int, _ max: Int) -> String {
        "this value is outside the typical range of \(min) to \(max)."
    }
}
