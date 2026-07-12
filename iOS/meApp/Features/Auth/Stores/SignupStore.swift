//
//  SignupStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

// swiftlint:disable file_length
// This store intentionally aggregates all signup flow logic to maintain
// a single source of truth for the multi-step signup process. Splitting would
// fragment state management and reduce maintainability.
import Combine
import Foundation
import SwiftUI

// MARK: SignupStore
/// This store is responsible for managing the signup process.
@MainActor
// swiftlint:disable:next type_body_length
final class SignupStore: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var accountService: AccountServiceProtocol
    @Injector var babyService: BabyServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var kvStorage: KvStorageServiceProtocol
    var alertLang = AlertStrings.self
    var loaderLang = LoaderStrings.self
    var commonLang = CommonStrings.self

    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            // (Re)entering Add Baby for a NEW baby (not an edit) must start from a clean
            // form. The keyboard blur that fires as the user leaves this step marks the
            // already-reset name field as touched, so without this the empty field shows a
            // phantom "Required." error when the user navigates back to it. Edits keep their
            // populated values (isEditingBabyIndex is set before navigating).
            if currentStep == .addBaby, isEditingBabyIndex == nil {
                resetBabyProfileForm()
            }
            updateNextButtonState()
        }
    }
    @Published private(set) var currentStep: SignupStep = .name
    @Published private(set) var steps: [SignupStep] = []
    @Published var signupForm = SignupForm()
    @Published var isNextEnabled = false
    @Published var isGoalSkipped = false

    // Device type selection
    @Published var selectedDeviceType: SignupDeviceType?
    // Devices already added during this signup session — shown as disabled on the pick screen
    @Published var disabledDeviceTypes: Set<SignupDeviceType> = []
    // The last device type the user completed — used for the "connect another device" screen title
    @Published var lastCompletedDeviceType: SignupDeviceType?
    // Accumulates every device type confirmed through connectAnotherDevice().
    // Used by setInitialProductTypes to send ALL selected devices on FINISH.
    @Published var registeredDeviceTypes: [SignupDeviceType] = []
    // Per-device save status populated during FINISH submission.
    // Drives the allProfilesReady / signupError terminal screens.
    @Published var deviceStatuses: [(device: SignupDeviceType, status: SignupDeviceStatus)] = []

    // Baby management
    @Published var babies: [SignupBaby] = []
    @Published var isEditingBabyIndex: Int?
    @Published var showBabyDatePicker = false
    @Published var babyProfileForm = BabyProfileSetupForm()

    // Height-related published properties
    @Published var selectedHeightInches: [String] = ["6", "5"]  // Default 6'5"
    @Published var selectedHeightCm: [String] = ["1", "9", "6"]  // Default 196cm
    @Published var showHeightInchesPicker = false
    @Published var showHeightCmPicker = false

    @Published var isFromAccountSwitching: Bool = false

    var onSignupSuccess: (() -> Void)?
    var dismissAction: (() -> Void)?

    let heightInchesOptions = ConversionTools.heightInchesOptions
    let heightCmOptions     = ConversionTools.heightCmOptions

    private let toastLang = ToastStrings.self
    private var cancellables = Set<AnyCancellable>()
    private var previousMetricValue: Bool = false

    private let tag = "SignupStore"
    private var isFinalizingSignup = false

    init() {
        // Resolve once per store instance to avoid cross-test DI races when
        // async step actions execute after other suites reset the container.
        _ = notificationService
        _ = accountService
        _ = babyService
        _ = logger
        _ = kvStorage

        previousMetricValue = signupForm.useMetric.value
        setupFormObservers()
        self.updateWeightValidators(isMetric: self.signupForm.useMetric.value)
        updateHeightPickerValues(from: Int(signupForm.height.value))
        self.updateWeightValidators(isMetric: self.signupForm.useMetric.value)
        rebuildSteps()
        currentStep = steps[currentStepIndex]
    }

    /// Builds the ordered steps for the current signup flow.
    /// Called only at navigation milestones (init, device pick, connect-another-device)
    /// so the published `steps` array stays stable for the duration of a single device loop.
    /// Recomputing reactively on form-field changes would shift indices under the SwiperView
    /// and cause visual jumps without a Next tap.
    private func computeSteps() -> [SignupStep] {
        let isSubsequentDevice = !disabledDeviceTypes.isEmpty
        let skipSex = isSubsequentDevice && !signupForm.gender.value.isEmpty

        // Email is the 2nd step (per design): Name → Email → Birthday → Pick a Device
        // On subsequent device loops email is already collected — skip it
        var result: [SignupStep] = isSubsequentDevice
            ? [.profileReady, .pickNextDevice]
            : [.name, .email, .dateOfBirth, .pickDevice]

        switch selectedDeviceType {
        case .babyScale:
            // Baby: add baby → baby list; no sex/height/goal
            result += [.addBaby, .babyList]
        case .bpm:
            // BPM: only sex (skip if already collected in a prior device loop)
            if !skipSex {
                result.append(.sex)
            }
        default:
            // weightScale (and nil/unselected)
            if !skipSex {
                result.append(.sex)
            }
            result += [.height, .goal]
        }

        // Password is per-account: only collected on the first device loop
        if !isSubsequentDevice {
            result.append(.password)
        }

        result.append(.profileReady)
        // Terminal screens appended so currentStepIndex can point to them after FINISH.
        // They are never shown during normal forward navigation.
        result += [.allProfilesReady, .signupError]
        return result
    }

    private func rebuildSteps() {
        steps = computeSteps()
    }

    /// True while there are still device types the user has not yet added.
    /// Used to show or hide the "CONNECT ANOTHER DEVICE" button.
    var canConnectAnotherDevice: Bool {
        disabledDeviceTypes.count < SignupDeviceType.allCases.count - 1
    }

    // Snapshotted when connectAnotherDevice() is called so the progress bar
    // doesn't reset to near-zero on the pickNextDevice screen.
    private var savedProgressValue: Double = 0

    var progressValue: Double {
        if currentStep == .pickNextDevice {
            return savedProgressValue
        }
        let terminalSteps: Set<SignupStep> = [.allProfilesReady, .signupError]
        let visibleCount = steps.filter { !terminalSteps.contains($0) }.count
        guard visibleCount > 0 else { return 1.0 }
        let clampedIndex = min(currentStepIndex + 1, visibleCount)
        return Double(clampedIndex) / Double(visibleCount)
    }

    /// All device types completed so far (registered + current selection if not yet registered).
    var allCompletedDevices: [SignupDeviceType] {
        guard let current = selectedDeviceType else { return registeredDeviceTypes }
        return registeredDeviceTypes.contains(current)
            ? registeredDeviceTypes
            : registeredDeviceTypes + [current]
    }

    var profileReadyTitle: String {
        if !canConnectAnotherDevice {
            return SignupStrings.AllProfilesReadyStep.title
        }
        let currentDevice = selectedDeviceType ?? lastCompletedDeviceType
        var done = registeredDeviceTypes
        if let device = currentDevice, !done.contains(device) { done.append(device) }

        if done.count >= 2 {
            let names = done.map(\.profileReadyName).joined(separator: " & ")
            return SignupStrings.ProfileReadyStep.multiDeviceTitle(names: names)
        }
        return currentDevice?.profileReadyTitle ?? SignupStrings.ProfileReadyStep.weightScaleTitle
    }

    /// Title shown on the "Connect Another Device" screen.
    /// Shows a combined title once 2+ devices are fully registered (both disabled),
    /// otherwise shows the single last-completed device title.
    var pickNextDeviceTitle: String {
        if registeredDeviceTypes.count >= 2 {
            let names = registeredDeviceTypes.map(\.profileReadyName).joined(separator: " & ")
            return SignupStrings.ProfileReadyStep.multiDeviceTitle(names: names)
        }
        return lastCompletedDeviceType?.profileReadyTitle
            ?? SignupStrings.ProfileReadyStep.weightScaleTitle
    }

    // MARK: - Height Management

    func updateHeightPickerValues(from storedHeight: Int) {
        let selections = ConversionTools.pickerSelections(from: storedHeight)
        selectedHeightInches = selections.inches
        selectedHeightCm     = selections.cm
    }

    func getFormattedHeight() -> String {
        let storedHeight = Int(signupForm.height.value)
        return ConversionTools.convertToFormattedHeight(storedHeight, isMetric: signupForm.useMetric.value)
    }

    func updateFormHeight(fromMetric: Bool, values: [String]) {
        // Validate height before updating
        guard ConversionTools.isValidHeightPickerValues(fromMetric: fromMetric, values: values) else {
            logger.log(level: .error, tag: tag, message: "Invalid height values rejected: \(values)")
            return
        }

        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            // Double-check cm is valid
            guard ConversionTools.isValidHeightCm(cm) else {
                logger.log(level: .error, tag: tag, message: "Invalid cm height rejected: \(cm)")
                return
            }
            signupForm.height.value = Double(ConversionTools.convertCmToStoredHeight(cm))
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
            // Double-check feet/inches is valid
            guard ConversionTools.isValidHeightInches(feet: feet, inches: inches) else {
                logger.log(level: .error, tag: tag, message: "Invalid feet/inches height rejected: \(feet)'\(inches)\"")
                return
            }
            let totalInches = (feet * 12) + inches
            signupForm.height.value = Double(ConversionTools.convertInchesToStoredHeight(totalInches))
        }
        // Update both picker values to stay in sync
        updateHeightPickerValues(from: Int(signupForm.height.value))
    }

    func showHeightPicker() {
        if signupForm.useMetric.value {
            showHeightCmPicker = true
        } else {
            showHeightInchesPicker = true
        }
    }

    // MARK: - Navigation

    func handleSkip() {
        switch currentStep {
        case .goal:
            isGoalSkipped = true
            signupForm.resetGoal()
            objectWillChange.send()
            moveToNextStep()
        case .addBaby:
            if isEditingBabyIndex != nil {
                showSkipEditBabyAlert()
            } else {
                showSkipAddBabyAlert()
            }
        default:
            objectWillChange.send()
            moveToNextStep()
        }
    }

    private func showSkipAddBabyAlert() {
        let lang = AlertStrings.SkipAddBabyAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.skipButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.resetBabyProfileForm()
                    self.objectWillChange.send()
                    let jumpTarget: SignupStep = self.steps.contains(.password) ? .password : .profileReady
                    if let targetIndex = self.steps.firstIndex(of: jumpTarget) {
                        self.currentStepIndex = targetIndex
                    }
                },
                AlertButtonModel(title: lang.goBackButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func showSkipEditBabyAlert() {
        let lang = AlertStrings.SkipEditBabyAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.skipButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.resetBabyProfileForm()
                    self.isEditingBabyIndex = nil
                    self.objectWillChange.send()
                    if let babyListIndex = self.steps.firstIndex(of: .babyList) {
                        self.currentStepIndex = babyListIndex
                    }
                },
                AlertButtonModel(title: lang.goBackButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    func moveToNextStep() {
        // createUser is deferred for all device types — called only when FINISH is tapped
        // When leaving addBaby, validate for duplicates then save the baby and move to babyList
        if currentStep == .addBaby {
            guard !hasDuplicateBabyName() else { return }
            saveBabyFromForm()
        }
        guard currentStepIndex < steps.count - 1 else { return }
        currentStepIndex += 1
    }

    /// Called from the password step "Complete" button — creates the account only.
    /// Sets isSignupInProgress so ContentViewModel does not navigate to dashboard yet.
    /// On success advances to the profileReady slide.
    func createAccount() {
        guard !accountService.isSignupInProgress else { return }
        Task {
            await performCreateAccount()
        }
    }

    /// Called from the profileReady step "FINISH" button — saves device profiles and finalizes.
    /// Clears isSignupInProgress so ContentViewModel can navigate to dashboard.
    func finishSignup() {
        guard !isFinalizingSignup else { return }
        isFinalizingSignup = true
        Task {
            await performSaveDevicesAndFinalize()
            isFinalizingSignup = false
        }
    }

    /// Called from the error screen — retries only the devices that failed.
    func retryFailedDevices() {
        Task {
            await retryDeviceSaves()
        }
    }

    /// Called from the error screen CANCEL button — discards the entire signup and resets.
    func cancelSignup(router: Router<AuthRoute>? = nil) {
        resetForm()
        if isFromAccountSwitching {
            dismissAction?()
        } else {
            router?.navigateBack()
        }
    }

    /// Called from the success screen DONE button.
    func completeSignup() {
        if let onSignupSuccess {
            onSignupSuccess()
        } else if isFromAccountSwitching {
            dismissAction?()
        }
        resetForm()
    }

    func connectAnotherDevice() {
        // Fixed at 0.9 so the bar reads ~90% on every iteration — the subsequent-device
        // loop has far fewer steps (profileReady + pickNextDevice + profileReady = 3),
        // and a computed ratio would drop to ~67% on the second pass.
        savedProgressValue = 0.9
        if let current = selectedDeviceType {
            lastCompletedDeviceType = current
            disabledDeviceTypes.insert(current)
            registeredDeviceTypes.append(current)
        }
        selectedDeviceType = nil
        rebuildSteps()
        guard let pickNextDeviceIndex = steps.firstIndex(of: .pickNextDevice) else { return }
        currentStepIndex = pickNextDeviceIndex
    }

    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
        if currentStep == .goal {
            isGoalSkipped = false
        }
        // If the user skipped addBaby (no babies saved), babyList is meaningless on back — skip over it.
        if currentStep == .babyList && babies.isEmpty {
            guard currentStepIndex > 0 else { return }
            currentStepIndex -= 1
        }
    }

    /// Called when user changes device type on the Pick a Device step.
    func selectDeviceType(_ type: SignupDeviceType) {
        selectedDeviceType = type
        // Keep signupForm.productTypes in sync so generateProfile() sends the correct
        // productType to the signup API and conditional validators (gender/dob/height)
        // reflect the current selection. Accumulated types include all already-confirmed
        // devices plus the new selection.
        signupForm.productTypes = registeredDeviceTypes.map(\.productType) + [type.productType]
        rebuildSteps()
        updateNextButtonState()
    }

    // MARK: - Baby Management

    /// Re-evaluates whether the current name duplicates another baby's name and sets or
    /// clears `duplicateNameError` accordingly. Called on every name change so the error
    /// surfaces (and the Next button disables) as soon as a duplicate is typed.
    @discardableResult
    private func refreshDuplicateBabyNameError() -> Bool {
        let trimmed = babyProfileForm.name.value.trimmingCharacters(in: .whitespaces).lowercased()
        guard !trimmed.isEmpty else {
            babyProfileForm.duplicateNameError = nil
            return false
        }
        let isDuplicate = babies.enumerated().contains { index, baby in
            index != isEditingBabyIndex &&
            baby.name.trimmingCharacters(in: .whitespaces).lowercased() == trimmed
        }
        babyProfileForm.duplicateNameError = isDuplicate ? SignupStrings.AddBabyStep.duplicateName : nil
        return isDuplicate
    }

    /// Returns true and marks the name field with a duplicate error if another baby already has the same name.
    private func hasDuplicateBabyName() -> Bool {
        let isDuplicate = refreshDuplicateBabyNameError()
        if isDuplicate {
            babyProfileForm.name.markAsTouched()
        }
        return isDuplicate
    }

    /// Saves the current baby form fields as a new baby (or updates an existing one).
    func saveBabyFromForm() {
        let baby = SignupBaby(
            name: babyProfileForm.name.value,
            birthday: babyProfileForm.birthday.value,
            sex: Sex(rawInput: babyProfileForm.biologicalSex.value),
            selectedWeightUnit: babyProfileForm.selectedWeightUnit,
            birthLengthInches: babyProfileForm.parsedBirthLengthInches,
            birthWeightLbs: babyProfileForm.parsedBirthWeightLbs,
            birthWeightOz: babyProfileForm.parsedBirthWeightOz
        )
        if let editIndex = isEditingBabyIndex {
            babies[editIndex] = baby
            isEditingBabyIndex = nil
        } else {
            babies.append(baby)
        }
        resetBabyProfileForm()
    }

    /// Navigates to the addBaby step to add another baby from the baby list.
    func addAnotherBaby() {
        resetBabyProfileForm()
        isEditingBabyIndex = nil
        guard let addBabyIndex = steps.firstIndex(of: .addBaby) else { return }
        currentStepIndex = addBabyIndex
    }

    /// Loads a baby into the form for editing and navigates to the addBaby step.
    func editBaby(at index: Int) {
        guard index < babies.count else { return }
        let baby = babies[index]
        resetBabyProfileForm()
        babyProfileForm.name.value = baby.name
        babyProfileForm.birthday.value = baby.birthday
        babyProfileForm.biologicalSex.value = baby.sex?.rawValue ?? ""
        babyProfileForm.populateStoredMeasurements(
            birthLengthInches: baby.birthLengthInches,
            birthWeightLbs: baby.birthWeightLbs,
            birthWeightOz: baby.birthWeightOz,
            preferredWeightUnit: baby.selectedWeightUnit
        )
        // Populating sets values (marking controls dirty); clear that so the edit screen
        // opens without surfacing validation errors until the user actually edits a field.
        markBabyProfileFormPristine()
        isEditingBabyIndex = index
        guard let addBabyIndex = steps.firstIndex(of: .addBaby) else { return }
        currentStepIndex = addBabyIndex
    }

    /// Shows a confirmation alert before removing a baby at the given index.
    func confirmDeleteBaby(at index: Int) {
        notificationService.showDeleteBabyConfirmation { [weak self] in
            self?.deleteBaby(at: index)
        }
    }

    /// Removes a baby at the given index.
    func deleteBaby(at index: Int) {
        guard index < babies.count else { return }
        babies.remove(at: index)
        updateNextButtonState()
    }

    // swiftlint:disable:next cyclomatic_complexity
    func updateNextButtonState() {
        switch currentStep {
        case .name:
            isNextEnabled = (signupForm.firstName.isValid && signupForm.lastName.isValid)
        case .dateOfBirth:
            isNextEnabled = (signupForm.birthday.isValid)
        case .pickDevice:
            isNextEnabled = selectedDeviceType != nil
        case .addBaby:
            isNextEnabled = babyProfileForm.isProfileValid && babyProfileForm.duplicateNameError == nil
        case .babyList:
            isNextEnabled = !babies.isEmpty
        case .sex:
            isNextEnabled = signupForm.gender.isValid
        case .height:
            isNextEnabled = isHeightValid
        case .goal:
            isNextEnabled = isGoalStepValid()
        case .email:
            isNextEnabled = signupForm.email.isValid
        case .password:
            // Check individual field validations AND form-level password match validation
            let fieldsValid = signupForm.password.isValid && signupForm.confirmPassword.isValid && signupForm.zipcode.isValid
            let passwordsMatch = !signupForm.formErrors[.passwordMatch]
            isNextEnabled = fieldsValid && passwordsMatch
        case .pickNextDevice:
            isNextEnabled = selectedDeviceType != nil
        case .profileReady:
            isNextEnabled = true
        case .allProfilesReady, .signupError:
            isNextEnabled = true
        }
    }

    /// Checks if the current height value is valid.
    /// For metric: height must be >= 100 cm
    /// For imperial: height must be >= 2'0" (24 inches)
    private var isHeightValid: Bool {
        let storedHeight = Int(signupForm.height.value)

        if signupForm.useMetric.value {
            let cm = ConversionTools.convertStoredHeightToCm(storedHeight)
            return ConversionTools.isValidHeightCm(cm)
        } else {
            let feetInches = ConversionTools.convertStoredHeightToFeet(storedHeight)
            return ConversionTools.isValidHeightInches(feet: feetInches[0], inches: feetInches[1])
        }
    }

    func getError<T>(for control: FormControl<T>) -> String? {
        signupForm.getError(for: control)
    }

    // MARK: - Field Touch / Validation (Signup)

    /// Marks a specific field as touched and triggers validation.
    /// Used by signup input views to show field errors as soon as the user leaves a field
    /// or presses the keyboard "Next/Done" button.
    /// - Parameter field: The field to touch and validate.
    func touchAndValidate(field: FocusField) {
        var didUpdate = true

        switch field {
        case .firstName:
            signupForm.firstName.markAsTouched()
            signupForm.firstName.validate()
        case .lastName:
            signupForm.lastName.markAsTouched()
            signupForm.lastName.validate()
        case .email:
            signupForm.email.markAsTouched()
            signupForm.email.validate()
        case .password:
            signupForm.password.markAsTouched()
            signupForm.password.validate()
            signupForm.validate()
        case .confirmPassword:
            signupForm.confirmPassword.markAsTouched()
            signupForm.confirmPassword.validate()
            signupForm.validate()
        case .zipCode:
            signupForm.zipcode.markAsTouched()
            signupForm.zipcode.validate()
        case .currentWeight:
            signupForm.currentWeight.markAsTouched()
            signupForm.currentWeight.validate()
            signupForm.validate()
        case .goalWeight:
            signupForm.goalWeight.markAsTouched()
            signupForm.goalWeight.validate()
            signupForm.validate()
        default:
            didUpdate = false
        }

        if didUpdate {
            objectWillChange.send()
        }
    }

    /// Call this from `onEditingChanged` for fields where we want to validate on blur.
    func handleEditingChanged(_ isEditing: Bool, field: FocusField) {
        guard !isEditing else { return }
        touchAndValidate(field: field)
    }

    func handleExit(router: Router<AuthRoute>? = nil) {
        // On Profile Ready the account already exists — close navigates to Dashboard
        if currentStep == .profileReady {
            accountService.markSignupInProgress(false)
            if isFromAccountSwitching {
                dismissAction?()
            }
            resetForm()
            return
        }
        // If the form is not dirty, dismiss the signup screen
        if !signupForm.isDirty {
            if isFromAccountSwitching {
                dismissAction?()
            } else {
                router?.navigateBack()
            }
            return
        }
        let alert = AlertModel(
            title: alertLang.SignupExitAlert.title,
            message: alertLang.SignupExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.SignupExitAlert.exitButton, type: .primary) { _ in
                    if self.isFromAccountSwitching {
                        self.dismissAction?()
                    } else {
                        router?.navigateBack()
                        self.resetForm()
                    }
                },
                AlertButtonModel(title: alertLang.SignupExitAlert.returnButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView {
                self.notificationService.dismissModal()
            })
        ))
    }

    func performCreateAccount() async {
        accountService.markSignupInProgress(true)
        notificationService.showLoader(LoaderModel(text: loaderLang.creatingAccount))

        let email = removeWhiteSpace(signupForm.email.value)
        let password = signupForm.password.value
        logger.log(level: .info, tag: tag, message: "Signup flow started. accountSwitching=\(isFromAccountSwitching)")

        let profile = generateProfile()

        do {
            try await accountService.signUp(email: email, password: password, profile: profile)
        } catch {
            accountService.markSignupInProgress(false)
            notificationService.dismissLoader()
            logger.log(
                level: .error,
                tag: tag,
                message: "Signup account creation failed. error=\(error.localizedDescription)"
            )
            if case AccountError.maxAccountsReached = error {
                showMaxUserAccountsAlert()
                return
            }
            handleSignupError(error)
            return
        }

        guard let account = accountService.activeAccount else {
            accountService.markSignupInProgress(false)
            notificationService.dismissLoader()
            handleSignupError(AccountError.noActiveAccount)
            return
        }

        persistSelectedSignupDeviceType(for: account.accountId)
        notificationService.dismissLoader()
        moveToNextStep()
    }

    func performSaveDevicesAndFinalize() async {
        guard let account = accountService.activeAccount else {
            accountService.markSignupInProgress(false)
            handleSignupError(AccountError.noActiveAccount)
            return
        }

        notificationService.showLoader(LoaderModel(text: loaderLang.creatingAccount))

        let goal = generateGoalRequest()

        // Build ordered list of all devices selected this session.
        var allDevices: [SignupDeviceType] = registeredDeviceTypes
        if let current = selectedDeviceType, !allDevices.contains(current) {
            allDevices.append(current)
        }

        // Initialize statuses as pending before any saves.
        deviceStatuses = allDevices.map { ($0, .pending) }

        // Save each device profile individually, recording per-device status.
        for (index, deviceType) in allDevices.enumerated() {
            do {
                try await saveDeviceProfile(deviceType: deviceType, account: account, goal: goal)
                deviceStatuses[index] = (deviceType, .success)
                logger.log(
                    level: .success,
                    tag: tag,
                    message: "Device profile saved. deviceType=\(deviceType.rawValue)"
                )
            } catch {
                deviceStatuses[index] = (deviceType, .failure(error))
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Device profile failed. deviceType=\(deviceType.rawValue), error=\(error.localizedDescription)"
                )
            }
        }

        // Persist all successfully-saved devices in a single productTypes write so
        // intermediate per-device updates (including ones from babyService.saveBaby)
        // don't clobber each other.
        await writeAccumulatedProductTypes()

        notificationService.dismissLoader()

        // Clear the gate before navigating so ContentViewModel can transition to dashboard.
        accountService.markSignupInProgress(false)

        let anyFailed = deviceStatuses.contains { if case .failure = $0.status { return true }; return false }
        if anyFailed {
            if let errorIndex = steps.firstIndex(of: .signupError) {
                currentStepIndex = errorIndex
            }
        } else {
            // A fresh signup should open on the multi-device snapshot overview, not jump
            // straight into a product dashboard. persistSignupBabies() persists a baby
            // selection (so the right baby is highlighted), which would otherwise be read
            // by resolveInitialProductRedirect() as a returning-user "last viewed product"
            // hint and redirect into that product. Clear it so the overview shows first;
            // the in-memory selection is kept.
            ProductTypeStore.shared.clearPersistedSelection()
            completeSignup()
        }
    }

    /// Retries only devices whose status is `.failure`. Succeeds → allProfilesReady, otherwise stays on error screen.
    private func retryDeviceSaves() async {
        guard let account = accountService.activeAccount else {
            handleSignupError(AccountError.noActiveAccount)
            return
        }
        let goal = generateGoalRequest()
        notificationService.showLoader(LoaderModel(text: loaderLang.creatingAccount))

        for (index, item) in deviceStatuses.enumerated() {
            guard case .failure = item.status else { continue }
            do {
                try await saveDeviceProfile(deviceType: item.device, account: account, goal: goal)
                deviceStatuses[index] = (item.device, .success)
                logger.log(
                    level: .success,
                    tag: tag,
                    message: "Retry succeeded. deviceType=\(item.device.rawValue)"
                )
            } catch {
                deviceStatuses[index] = (item.device, .failure(error))
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Retry failed. deviceType=\(item.device.rawValue), error=\(error.localizedDescription)"
                )
            }
        }

        await writeAccumulatedProductTypes()

        notificationService.dismissLoader()

        let anyFailed = deviceStatuses.contains { if case .failure = $0.status { return true }; return false }
        if !anyFailed, let successIndex = steps.firstIndex(of: .allProfilesReady) {
            currentStepIndex = successIndex
        }
    }

    /// Saves the per-device side-effects (babies, goal) for a single device.
    /// Does NOT write `productTypes` — that's done once after the loop so individual
    /// writes don't clobber each other.
    private func saveDeviceProfile(
        deviceType: SignupDeviceType,
        account: AccountSnapshot,
        goal: Goal?
    ) async throws {
        if deviceType == .babyScale {
            try await persistSignupBabies(for: account)
        }

        if deviceType == .weightScale, let goal = goal {
            logger.log(
                level: .info,
                tag: tag,
                message: "Creating goal. goalType=\(goal.goalType.rawValue), goalWeight=\(goal.goalWeight)"
            )
            _ = try await accountService.createGoal(goal)
        }
    }

    /// Writes the union of product types for every device that saved successfully.
    /// `updateProductTypes` replaces the array, so we must send all of them in one call.
    ///
    /// This is a best-effort write, NOT a signup gate. The substantive work — creating
    /// the account and saving each device profile (goal, babies) — has already succeeded
    /// by the time we get here, and product types are self-healing state:
    ///   • the server auto-adds a product when its device is paired / a baby is created, and
    ///   • `ProductTypeStore.resolveProductTypes()` reconstructs product types from synced
    ///     devices/babies and retries `updateProductTypes` on the next rebuild.
    /// A brand-new account's `PATCH /account/products` can transiently 404 before the
    /// account fully propagates server-side; flipping the already-saved devices to
    /// `.failure` here would surface the signup error screen for an account that was, in
    /// fact, created successfully. So on failure we log and let signup complete — the
    /// product types reconcile on the next sync.
    private func writeAccumulatedProductTypes() async {
        let successfulIndices = deviceStatuses.indices.filter {
            if case .success = deviceStatuses[$0].status { return true }
            return false
        }
        let productTypes = successfulIndices.map { deviceStatuses[$0].device.productType }
        guard !productTypes.isEmpty else { return }
        do {
            try await accountService.updateProductTypes(productTypes)
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to write accumulated product types; will reconcile on next sync. "
                    + "error=\(error.localizedDescription)"
            )
        }
    }

    // MARK: - Private Methods
    private func isGoalStepValid() -> Bool {
        // Use the form's isGoalValidForSave which checks: dirty, touched, and no errors
        signupForm.isGoalValidForSave
    }

    private func generateProfile() -> Profile {
        let formattedDOB = DateTimeTools.formatDateToYMD_Local(signupForm.birthday.value)
        let measurementUnits: String? = signupForm.useMetric.value
            ? MeasurementUnits.metric.rawValue
            : MeasurementUnits.imperialLbOz.rawValue

        return Profile(
            firstName: removeWhiteSpace(signupForm.firstName.value),
            lastName: removeWhiteSpace(signupForm.lastName.value),
            gender: Sex(rawValue: signupForm.gender.value) ?? .male,
            zipcode: removeWhiteSpace(signupForm.zipcode.value),
            dob: formattedDOB,
            weightUnit: signupForm.useMetric.value ? .kg : .lb,
            height: signupForm.height.value,
            activityLevel: .normal,
            productTypes: signupForm.productTypes,
            measurementUnits: measurementUnits
        )
    }

    private func generateGoalRequest() -> Goal? {
        guard !isGoalSkipped else { return nil }

        let useMetric = signupForm.useMetric.value
        let goalTypeValue = signupForm.goalType.value
        let current = Double(signupForm.currentWeight.value) ?? 0.0
        let target = Double(signupForm.goalWeight.value) ?? 0.0

        let convert = { (weight: Double) -> Int in
            ConversionTools.convertDisplayToStored(weight, forceMetric: useMetric)
        }

        if goalTypeValue == GoalType.maintain.rawValue {
            return Goal(
                type: .maintain,
                goalWeight: convert(target),
                initialWeight: 0,
                goalType: .maintain
            )
        } else {
            let derivedType: GoalType = target > current ? .gain : .lose
            return Goal(
                type: derivedType,
                goalWeight: convert(target),
                initialWeight: convert(current),
                goalType: derivedType
            )
        }
    }

    private func persistSignupBabies(for account: AccountSnapshot) async throws {
        guard !babies.isEmpty else { return }

        var firstSavedSelection: ProductSelection?

        for signupBaby in babies {
            let savedBaby = try await babyService.saveBaby(
                name: signupBaby.name,
                accountId: account.accountId,
                deviceId: nil,
                birthday: signupBaby.birthday,
                biologicalSex: signupBaby.sex?.rawValue,
                birthLengthInches: signupBaby.birthLengthInches,
                birthWeightLbs: signupBaby.birthWeightLbs,
                birthWeightOz: signupBaby.birthWeightOz
            )

            if firstSavedSelection == nil {
                let profile = BabyProfile(
                    id: savedBaby.id,
                    name: savedBaby.name,
                    deviceId: savedBaby.deviceId,
                    birthday: savedBaby.birthday,
                    biologicalSex: savedBaby.biologicalSex,
                    birthLengthInches: savedBaby.birthLengthInches,
                    birthWeightLbs: savedBaby.birthWeightLbs,
                    birthWeightOz: savedBaby.birthWeightOz
                )
                firstSavedSelection = .baby(profile: profile)
            }
        }

        if let firstSavedSelection {
            ProductTypeStore.shared.selectLastAdded(firstSavedSelection)
            logger.log(
                level: .info,
                tag: tag,
                message: "Persisted \(babies.count) signup babies and selected \(firstSavedSelection.displayName)"
            )
        }
    }

    private func resetBabyProfileForm() {
        babyProfileForm.reset()
    }

    /// Clears dirty/touched state on the baby form's controls without changing their values.
    /// Used after programmatically populating the form for an edit so validation errors
    /// don't appear until the user interacts.
    private func markBabyProfileFormPristine() {
        babyProfileForm.name.markAsPristine()
        babyProfileForm.name.markAsUntouched()
        babyProfileForm.birthday.markAsPristine()
        babyProfileForm.birthday.markAsUntouched()
        babyProfileForm.biologicalSex.markAsPristine()
        babyProfileForm.biologicalSex.markAsUntouched()
        babyProfileForm.birthLengthInches.markAsPristine()
        babyProfileForm.birthLengthInches.markAsUntouched()
        babyProfileForm.birthWeightLbs.markAsPristine()
        babyProfileForm.birthWeightLbs.markAsUntouched()
        babyProfileForm.birthWeightOz.markAsPristine()
        babyProfileForm.birthWeightOz.markAsUntouched()
        babyProfileForm.birthLengthCm.markAsPristine()
        babyProfileForm.birthLengthCm.markAsUntouched()
        babyProfileForm.birthWeightKg.markAsPristine()
        babyProfileForm.birthWeightKg.markAsUntouched()
    }

    private func persistSelectedSignupDeviceType(for accountId: String) {
        // Persist the first device from the session as the primary signup device type.
        // This drives HealthKit permission scoping — use the user's first intent.
        let primaryDevice = registeredDeviceTypes.first ?? selectedDeviceType
        guard let primaryDevice else { return }
        let key = KvStorageKeys.selectedSignupDeviceTypeKey(for: accountId)
        kvStorage.setValue(primaryDevice.rawValue, forKey: key)
        kvStorage.setValue(accountId, forKey: KvStorageKeys.recentSignupAccountId.rawValue)
        kvStorage.setValue(primaryDevice.rawValue, forKey: KvStorageKeys.recentSignupDeviceType.rawValue)
        logger.log(
            level: .info,
            tag: tag,
            message: "Persisted signup-selected device type. accountId=\(accountId), deviceType=\(primaryDevice.rawValue)"
        )
    }

    private func handleSignupError(_ error: Error) {
        var toastMessage: String?
        let toastTitle: String = toastLang.errorCreatingAccount

        switch error {
        case HTTPError.apiError(let message, _):
            if message == commonLang.emailAlreadyInUse {
                toastMessage = toastLang.emailInUse
            } else {
                toastMessage = toastLang.somethingWentWrong
            }
        case HTTPError.badRequest:
            toastMessage = toastLang.emailInUse
        case HTTPError.noInternet:
            break // No message needed, handled by NetworkMonitor
        case HTTPError.serverError:
            toastMessage = toastLang.serverError
        default:
            toastMessage = toastLang.somethingWentWrong
        }

        if let message = toastMessage {
            notificationService.showToast(ToastModel(title: toastTitle, message: message))
        }
        logger.log(
            level: .error,
            tag: tag,
            message: "Signup error handled. mappedToastShown=\(toastMessage != nil), "
                + "errorType=\(String(describing: type(of: error)))"
        )
    }

    private func setupFormObservers() { // swiftlint:disable:this function_body_length
        signupForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)

        babyProfileForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)

        babyProfileForm.name.$value
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.refreshDuplicateBabyNameError()
                self.updateNextButtonState()
            }
            .store(in: &cancellables)

        // React to password-related changes only when we're on the password step
        let passwordChanges = Publishers.CombineLatest(
            signupForm.password.$value,
            signupForm.confirmPassword.$value
        )

        passwordChanges
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _, _ in
                self?.signupForm.validate()

                if self?.currentStep == .password {
                    self?.updateNextButtonState()
                }
            }
            .store(in: &cancellables)

        // Update button state when form errors change (password step only)
        signupForm.$formErrors
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard self?.currentStep == .password else { return }
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)

        // Observe useMetric changes
        signupForm.useMetric.$value
            .dropFirst()
            .sink { [weak self] isMetric in
                guard let self = self else { return }
                let oldMetricValue = self.previousMetricValue

                // Update validators first to ensure validation uses correct unit constraints
                self.updateWeightValidators(isMetric: isMetric)

                // Convert weight values when switching units
                if !self.signupForm.currentWeight.value.isEmpty {
                    let convertedCurrentWeight = ConversionTools.convertDisplayWeightValue(
                        self.signupForm.currentWeight.value,
                        fromMetric: oldMetricValue,
                        toMetric: isMetric
                    )
                    self.signupForm.currentWeight.value = convertedCurrentWeight
                    self.signupForm.currentWeight.validate()
                }

                if !self.signupForm.goalWeight.value.isEmpty {
                    let convertedGoalWeight = ConversionTools.convertDisplayWeightValue(
                        self.signupForm.goalWeight.value,
                        fromMetric: oldMetricValue,
                        toMetric: isMetric
                    )
                    self.signupForm.goalWeight.value = convertedGoalWeight
                    self.signupForm.goalWeight.validate()
                }

                self.updateHeightPickerValues(from: Int(self.signupForm.height.value))

                // Update previous value for next change
                self.previousMetricValue = isMetric
            }
            .store(in: &cancellables)
    }

    private func updateWeightValidators(isMetric: Bool) {
        let maxWeight = isMetric ? 450.0 : 999.0
        // Remove old validator
        signupForm.currentWeight.removeValidator(ofType: .maxValue)
        signupForm.goalWeight.removeValidator(ofType: .maxValue)

        // Add new validator
        let validator = Validator.maxValue(maxWeight)
        signupForm.currentWeight.addValidator(validator)
        signupForm.goalWeight.addValidator(validator)
    }

    // MARK: - Form Reset

    /// Resets the signup flow back to its initial pristine state.
    /// This is useful when the user abandons the signup process and we want to
    /// discard all entered data while also preventing Combine subscription leaks.
    func resetForm() {
        // Cancel existing subscriptions so we don't leak memory.
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()

        // Replace with a brand-new form instance.
        signupForm = SignupForm()

        // Reset navigation/UI state.
        selectedDeviceType = nil
        lastCompletedDeviceType = nil
        savedProgressValue = 0
        disabledDeviceTypes = []
        registeredDeviceTypes = []
        deviceStatuses = []
        babies.removeAll()
        isEditingBabyIndex = nil
        showBabyDatePicker = false
        currentStepIndex = 0
        isGoalSkipped = false
        isNextEnabled = false
        showHeightInchesPicker = false
        showHeightCmPicker = false
        previousMetricValue = signupForm.useMetric.value

        // Sync height pickers with the default form height.
        updateHeightPickerValues(from: Int(signupForm.height.value))

        // Re-establish observers that depend on the new form instance.
        setupFormObservers()

        // Ensure the primary action button reflects the current (reset) state.
        updateNextButtonState()
    }

    /// Presents an alert informing the user that the maximum number of accounts
    /// has been reached.
    private func showMaxUserAccountsAlert() {
        let alertLang = alertLang.MaxUsersAlert
        let alert = AlertModel(
            title: alertLang.title,
            message: isFromAccountSwitching ? alertLang.message : alertLang.logInAndRemoveMessage,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
}
