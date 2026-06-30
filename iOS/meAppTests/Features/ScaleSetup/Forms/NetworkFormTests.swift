//
//  NetworkFormTests.swift
//  meAppTests
//

@testable import meApp
import Testing

@Suite("NetworkForm", .serialized)
@MainActor
struct NetworkFormTests {

    // MARK: - Initial state

    @Test("initial ssid value is empty")
    func initialSSIDEmpty() {
        let form = NetworkForm()
        #expect(form.ssid.value.isEmpty)
    }

    @Test("initial password value is empty")
    func initialPasswordEmpty() {
        let form = NetworkForm()
        #expect(form.password.value.isEmpty)
    }

    @Test("initial networkHasNoPassword is false")
    func initialNetworkHasNoPasswordFalse() {
        let form = NetworkForm()
        #expect(form.networkHasNoPassword == false)
    }

    // MARK: - SSID required validator

    @Test("empty ssid fails required")
    func emptySsidFails() {
        let form = NetworkForm()
        form.ssid.validate()
        #expect(form.ssid.errors[.required] == true)
    }

    @Test("non-empty ssid passes required")
    func nonEmptySsidPasses() {
        let form = NetworkForm()
        form.setSSID("HomeNetwork")
        form.ssid.validate()
        #expect(form.ssid.errors[.required] == false)
    }

    // MARK: - Password required validator

    @Test("empty password fails required when networkHasNoPassword is false")
    func emptyPasswordFailsRequired() {
        let form = NetworkForm()
        form.password.validate()
        #expect(form.password.errors[.required] == true)
    }

    @Test("non-empty password passes required")
    func nonEmptyPasswordPasses() {
        let form = NetworkForm()
        form.setPassword("secret123")
        form.password.validate()
        #expect(form.password.errors[.required] == false)
    }

    // MARK: - networkHasNoPassword toggle

    @Test("setting networkHasNoPassword true removes password required validator")
    func networkHasNoPasswordRemovesRequired() {
        let form = NetworkForm()
        form.networkHasNoPassword = true
        form.password.validate()
        #expect(form.password.errors[.required] == false)
    }

    @Test("setting networkHasNoPassword true clears password value")
    func networkHasNoPasswordClearsPassword() {
        let form = NetworkForm()
        form.setPassword("secret")
        form.networkHasNoPassword = true
        #expect(form.password.value.isEmpty)
    }

    @Test("setting networkHasNoPassword back to false restores required validator")
    func restoringPasswordRequiredValidator() {
        let form = NetworkForm()
        form.networkHasNoPassword = true
        form.networkHasNoPassword = false
        form.password.validate()
        #expect(form.password.errors[.required] == true)
    }

    // MARK: - setSSID

    @Test("setSSID updates ssid value")
    func setSSIDUpdates() {
        let form = NetworkForm()
        form.setSSID("MyWifi")
        #expect(form.ssid.value == "MyWifi")
    }

    // MARK: - setPassword

    @Test("setPassword updates password value")
    func setPasswordUpdates() {
        let form = NetworkForm()
        form.setPassword("Pass1234")
        #expect(form.password.value == "Pass1234")
    }

    // MARK: - clearSSIDAndMarkPristine

    @Test("clearSSIDAndMarkPristine resets ssid to empty and pristine")
    func clearSSIDAndMarkPristine() {
        let form = NetworkForm()
        form.setSSID("OldNetwork")
        form.ssid.markAsDirty()
        form.clearSSIDAndMarkPristine()
        #expect(form.ssid.value.isEmpty)
        #expect(form.ssid.isPristine == true)
    }

    // MARK: - isValidApModeSSID

    @Test("isValidApModeSSID returns true for AP mode SSID prefix")
    func apModeSSIDValid() {
        let form = NetworkForm()
        form.setSSID("gg_SmartDeviceSetup_abc123")
        #expect(form.isValidApModeSSID() == true)
    }

    @Test("isValidApModeSSID returns false for regular SSID")
    func regularSSIDNotApMode() {
        let form = NetworkForm()
        form.setSSID("HomeNetwork")
        #expect(form.isValidApModeSSID() == false)
    }

    @Test("isValidApModeSSID returns false for empty SSID")
    func emptySSIDNotApMode() {
        let form = NetworkForm()
        #expect(form.isValidApModeSSID() == false)
    }

    // MARK: - getRawValue

    @Test("getRawValue returns WifiConfig with ssid and password")
    func getRawValueWithPassword() {
        let form = NetworkForm()
        form.setSSID("HomeNet")
        form.setPassword("pass123")
        let config = form.getRawValue()
        #expect(config.ssid == "HomeNet")
        #expect(config.password == "pass123")
    }

    @Test("getRawValue returns WifiConfig with nil password when networkHasNoPassword")
    func getRawValueNoPassword() {
        let form = NetworkForm()
        form.setSSID("OpenNet")
        form.networkHasNoPassword = true
        let config = form.getRawValue()
        #expect(config.ssid == "OpenNet")
        #expect(config.password == nil)
    }

    // MARK: - reset

    @Test("reset clears ssid and password")
    func resetClearsForm() {
        let form = NetworkForm()
        form.setSSID("Net")
        form.setPassword("pass")
        form.reset()
        #expect(form.ssid.value.isEmpty)
        #expect(form.password.value.isEmpty)
        #expect(form.networkHasNoPassword == false)
    }

    // MARK: - getError

    @Test("getError returns nil for pristine untouched control")
    func getErrorNilForPristine() {
        let form = NetworkForm()
        let error = form.getError(for: form.ssid)
        #expect(error == nil)
    }

    @Test("getError returns required message for dirty empty ssid")
    func getErrorRequiredForDirty() {
        let form = NetworkForm()
        form.ssid.markAsDirty()
        form.ssid.validate()
        let error = form.getError(for: form.ssid)
        #expect(error != nil)
    }

    @Test("getError returns nil for password when networkHasNoPassword is true")
    func getErrorNilPasswordNoRequired() {
        let form = NetworkForm()
        form.networkHasNoPassword = true
        form.password.markAsDirty()
        form.password.markAsTouched()
        let error = form.getError(for: form.password)
        #expect(error == nil)
    }

    // MARK: - touchAndValidate helpers

    @Test("touchAndValidatePassword marks password touched and validates")
    func touchAndValidatePassword() {
        let form = NetworkForm()
        form.touchAndValidatePassword()
        #expect(form.password.isTouched == true)
    }

    @Test("touchAndValidateSSID marks ssid touched and validates")
    func touchAndValidateSSID() {
        let form = NetworkForm()
        form.touchAndValidateSSID()
        #expect(form.ssid.isTouched == true)
    }

    // MARK: - Form-level isValid

    @Test("form is valid when ssid and password are provided")
    func formValidWithBothFields() {
        let form = NetworkForm()
        form.setSSID("MyNet")
        form.setPassword("secret")
        #expect(form.isValid == true)
    }

    @Test("form is invalid when ssid is missing")
    func formInvalidNoSSID() {
        let form = NetworkForm()
        form.setPassword("secret")
        form.ssid.validate()
        #expect(form.isValid == false)
    }

    @Test("form is valid when ssid provided and networkHasNoPassword true")
    func formValidNoPassword() {
        let form = NetworkForm()
        form.setSSID("OpenNet")
        form.networkHasNoPassword = true
        #expect(form.isValid == true)
    }
}
