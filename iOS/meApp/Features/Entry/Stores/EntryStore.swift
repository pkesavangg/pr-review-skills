// swiftlint:disable file_length
import Combine
import Foundation
import SwiftUI

@MainActor
// swiftlint:disable:next type_body_length
final class EntryStore: ObservableObject {
    // Dependencies
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol
    @Injector var productTypeStore: ProductTypeStoreProtocol

    // Strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let alertLang = AlertStrings.ManualEntryExitAlert.self
    private let loaderLang = LoaderStrings.self

    // Product type injected via DI; drives which entry view is shown

    // Form & UI state
    @Published var manualEntryForm = ManualEntryForm()
    @Published var bpForm = BloodPressureEntryForm()
    @Published var babyForm = BabyEntryForm()
    @Published var babyWeightUnit: BabyWeightUnit = .lbsOz
    @Published var babyLengthUnit: BabyLengthUnit = .inches
    @Published var weightUnit: WeightUnit = .lb
    @Published var canShowOtherBodyMetrics = false
    @Published var showMetrics = false
    @Published var showDatePicker = false
    @Published var showTimePicker = false

    /// Prevent overlapping saves / double taps
    @Published private(set) var isSaving = false

    /// Controls whether BMI should be auto-calculated from weight/height
    @Published var isBmiAutoCalculationEnabled = true

    private let maxBmiValue: Double = 99.0
    private var cancellables = Set<AnyCancellable>()
    private var isAdjustingTime = false
    private var timeSyncCancellable: AnyCancellable?
    private var isTimeSyncActive = false
    private var hasUserAdjustedTime = false

    let tag = "EntryStore"

    var isBabyFormValid: Bool {
        // Weight is the required field and SAVE gates on it alone; ounces and length
        // are optional. In lb/oz mode the pounds field is the required one — a value
        // in ounces only (e.g. "0 lb 0.4 oz") must NOT enable SAVE (MOB-1548).
        let weightPresent: Bool
        let weightValid: Bool
        switch babyWeightUnit {
        case .kg:
            weightPresent = !babyForm.kg.value.isEmpty
            weightValid = babyForm.kg.isValid
        case .lb:
            weightPresent = !babyForm.lb.value.isEmpty
            weightValid = babyForm.lb.isValid
        case .lbsOz:
            weightPresent = !babyForm.pounds.value.isEmpty
            weightValid = babyForm.pounds.isValid && babyForm.ounces.isValid
        }

        // Length is optional; only its validity gates SAVE (an empty length is valid).
        let lengthValid: Bool
        switch babyLengthUnit {
        case .inches:
            lengthValid = babyForm.inches.isValid
        case .cm:
            lengthValid = babyForm.cm.isValid
        }

        return weightPresent && weightValid && lengthValid && babyForm.date.isValid
    }

    /// Lower bound for the baby entry date picker — see `ProductSelection.babyEntryMinimumDate`
    /// (birthday-anchored, Jan 1, 2000 fallback, ignores future birthdays; MOB-1567).
    var babyEntryMinimumDate: Date {
        productTypeStore.selectedItem.babyEntryMinimumDate
    }

    var maxSelectableTime: Date {
        if Calendar.current.isDateInToday(manualEntryForm.date.value) {
            let now = Date()
            let calendar = Calendar.current
            let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: now)
            return calendar.date(from: comps) ?? now
        } else {
            var comps = Calendar.current.dateComponents([.year, .month, .day], from: manualEntryForm.date.value)
            comps.hour = 23; comps.minute = 59
            return Calendar.current.date(from: comps) ?? Date()
        }
    }

    // MARK: - Init
    init() {
        deviceService.scalesPublisher
            .map { $0.contains { $0.bathScale?.scaleType == DeviceSourceType.btWifiR4.rawValue } }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .assign(to: \.canShowOtherBodyMetrics, on: self)
            .store(in: &cancellables)

        initializeObservers()
        setupBmiObservers()
        updateWeightValidators()
        setupDateTimeObservers()
        subscribeToProductTypeChanges()
    }

    private func subscribeToProductTypeChanges() {
        productTypeStore.selectedItemPublisher
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newItem in
                guard let self = self else { return }
                self.logger.log(
                      level: .info,
                      tag: self.tag,
                      message: "Product type changed to \(newItem.displayName)"
                  )
                self.resetBPForm()
                self.resetBabyForm()
                self.resetWeightForm()
            }
            .store(in: &cancellables)
    }

    // MARK: - Public helpers

    func getError<T>(for control: FormControl<T>) -> String? {
        manualEntryForm.getError(for: control, weightUnit: weightUnit)
    }

    /// Disable automatic BMI calculation (e.g., when user focuses the BMI field)
    func disableBmiAutoCalculation() {
        isBmiAutoCalculationEnabled = false
    }

    /// Re-enable automatic BMI calculation
    func enableBmiAutoCalculation() {
        isBmiAutoCalculationEnabled = true
    }

    /// Save entry with gating, no artificial sleeps, and minimal main-thread churn.
    /// Returns `true` only when the entry was persisted successfully; callers should
    /// gate post-save navigation on this result.
    @discardableResult
    func saveEntry() async -> Bool { // swiftlint:disable:this function_body_length
        guard !isSaving else { return false }
        isSaving = true
        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer {
            notificationService.dismissLoader()
            isSaving = false
        }

        // Ensure valid time relative to selected date
        clampTimeForSelectedDate()

        guard manualEntryForm.isValid else { return false }

        let entryTimestamp = DateTimeTools.isoString(
            date: manualEntryForm.date.value,
            time: manualEntryForm.time.value,
            useUTC: true,
            randomizeSubMinute: true
        )

        let weightStored = ConversionTools.convertDisplayToStored(
            Double(manualEntryForm.weight.value) ?? 0,
            forceMetric: false,
            isMetric: weightUnit == .kg
        )

        let bodyFat         = toTenths(manualEntryForm.bodyFat.value)
        let muscleMass      = toTenths(manualEntryForm.muscleMass.value)
        let water           = toTenths(manualEntryForm.bodyWater.value)
        let bmi             = toTenths(manualEntryForm.bmi.value)
        let boneMass        = toTenths(manualEntryForm.boneMass.value)
        let bmr             = toTenths(manualEntryForm.bmr.value)
        let metabolicAge    = Int(manualEntryForm.metabolicAge.value)
        let proteinPercent  = toTenths(manualEntryForm.protein.value)
        let pulse           = Int(manualEntryForm.heartRate.value)
        let skeletalMuscle  = toTenths(manualEntryForm.skeletalMuscles.value)
        let subcutaneousFat = toTenths(manualEntryForm.subcutaneousFat.value)
        let visceralFat     = toTenths(manualEntryForm.visceralFat.value)
        let unit            = weightUnit == .kg ? WeightUnit.kg.rawValue : WeightUnit.lb.rawValue

        guard let accountId = accountService.activeAccount?.accountId else { return false }

        let scaleEntry = BathScaleEntry(
            weight: weightStored,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: EntrySource.manual.rawValue
        )

        let scaleMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMuscle,
            subcutaneousFatPercent: subcutaneousFat,
            visceralFatLevel: visceralFat,
            boneMass: boneMass,
            impedance: nil,
            unit: unit
        )

        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            entryType: EntryType.scale.rawValue,
            isSynced: false
        )
        let note = manualEntryForm.notes.value.trimmingCharacters(in: .whitespacesAndNewlines)
        entry.note = note.isEmpty ? nil : note
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric

        do {
            logger.log(level: .info, tag: self.tag, message: "Manual entry save started. accountId=\(accountId), timestamp=\(entryTimestamp)")
            // Persist (should not be @MainActor inside service)
            try await entryService.saveNewEntry(entry)
            // Let event streams (entrySaved) trigger downstream reloads
            resetForm()
            // The save is now instant (~45ms), so the loader can't visibly appear — show a
            // success toast so the user gets clear feedback that the entry was saved
            // (MOB-1433 §5c). The toast is a global overlay, so it persists across the
            // post-save tab switch.
            notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
            logger.log(level: .success, tag: self.tag, message: "Manual entry save succeeded. accountId=\(accountId), timestamp=\(entryTimestamp)")
            return true
        } catch {
            logger.log(
                level: .error,
                tag: self.tag,
                message: "Failed to save manual entry. accountId=\(accountId), timestamp=\(entryTimestamp), error=\(error.localizedDescription)"
            )
            notificationService.showToast(ToastModel(title: toastLang.errorSavingEntry, message: toastLang.pleaseTryAgain))
            return false
        }
    }

    func refreshTimeOnTabSelected() {
        let now = Date()
        if Calendar.current.isDateInToday(manualEntryForm.date.value) {
            manualEntryForm.time.value = now
            manualEntryForm.time.markAsPristine()
        } else {
            let maxTime = maxSelectableTime
            if manualEntryForm.time.value > maxTime {
                manualEntryForm.time.value = maxTime
                manualEntryForm.time.markAsPristine()
            }
        }
    }

    /// Refreshes the weight unit from the active account.
    /// Call this when the view appears to ensure the unit is up-to-date after sync.
    func refreshWeightUnit() {
        updateWeightUnitFromAccount(accountService.activeAccount)
    }

    private func clampTimeForSelectedDate(selectedDate: Date? = nil, selectedTime: Date? = nil) {
        if isAdjustingTime { return }
        isAdjustingTime = true
        defer { isAdjustingTime = false }
        let date = selectedDate ?? manualEntryForm.date.value
        let time = selectedTime ?? manualEntryForm.time.value
        let now = Date()
        let combined = combine(date: date, time: time)
        let newValue: Date = {
            if Calendar.current.isDateInToday(date) {
                return combined > now ? now : combined
            } else {
                let endOfSelectedDay = endOfDay(for: date)
                return combined > endOfSelectedDay ? endOfSelectedDay : combined
            }
        }()
        if manualEntryForm.time.value != newValue {
            manualEntryForm.time.value = newValue
            manualEntryForm.time.markAsPristine()
        }
    }

    private func setupDateTimeObservers() {
        manualEntryForm.date.$value
            .removeDuplicates()
            .sink { [weak self] newDate in
                self?.clampTimeForSelectedDate(selectedDate: newDate)
            }
            .store(in: &cancellables)

        // If the user changes time while the picker is open, stop auto updates for this session
        manualEntryForm.time.$value
            .sink { [weak self] _ in
                guard let self = self else { return }
                if self.showTimePicker {
                    self.hasUserAdjustedTime = true
                }
            }
            .store(in: &cancellables)
    }

    private func combine(date: Date, time: Date) -> Date {
        let calendar = Calendar.current
        var dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: time)
        dateComponents.hour = timeComponents.hour
        dateComponents.minute = timeComponents.minute
        dateComponents.second = timeComponents.second
        return calendar.date(from: dateComponents) ?? date
    }

    private func endOfDay(for date: Date) -> Date {
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: date)
        comps.hour = 23; comps.minute = 59
        return Calendar.current.date(from: comps) ?? date
    }

    func populateFromAppSync(metrics: AppSyncEntryMetrics) {
        weightUnit = metrics.isMetric ? .kg : .lb
        updateWeightValidators()

        let displayWeight = ConversionTools.convertStoredToDisplay(metrics.storedWeight, isMetric: metrics.isMetric)
        manualEntryForm.weight.value = String(format: "%.1f", displayWeight)
        manualEntryForm.weight.validate()

        if let bmiStr = metrics.bmi {
            manualEntryForm.bmi.value = bmiStr
            manualEntryForm.bmi.validate()
        }

        assignPercent(metrics.bodyFat, to: manualEntryForm.bodyFat)
        assignPercent(metrics.muscleMass, to: manualEntryForm.muscleMass)
        assignPercent(metrics.waterWeight, to: manualEntryForm.bodyWater)

        showMetrics = true
    }

    private func initializeObservers() {
        // Observe account changes directly to catch all updates
        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] account in
                guard let self = self else { return }
                self.updateWeightUnitFromAccount(account)
            }
            .store(in: &cancellables)

        // Observe NotificationCenter for weightUnit changes (catches cases where @Published doesn't emit)
        NotificationCenter.default.publisher(for: .accountWeightUnitChanged)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                // Check and update weightUnit from current account
                self.updateWeightUnitFromAccount(self.accountService.activeAccount)
            }
            .store(in: &cancellables)
    }

    @MainActor private func updateWeightUnitFromAccount(_ account: AccountSnapshot?) {
        let unit = account?.weightUnit ?? .lb
        let newBabyWeightUnit = babyWeightUnitFor(account)
        let newBabyLengthUnit: BabyLengthUnit = newBabyWeightUnit == .kg ? .cm : .inches

        var didChange = false
        if self.weightUnit != unit {
            self.weightUnit = unit
            didChange = true
        }
        if self.babyWeightUnit != newBabyWeightUnit {
            self.babyWeightUnit = newBabyWeightUnit
            didChange = true
        }
        if self.babyLengthUnit != newBabyLengthUnit {
            self.babyLengthUnit = newBabyLengthUnit
            didChange = true
        }
        if didChange {
            self.updateWeightValidators()
            self.calculateBMI()
            self.objectWillChange.send()
        }
    }

    private func babyWeightUnitFor(_ account: AccountSnapshot?) -> BabyWeightUnit {
        guard let raw = account?.measurementUnits,
              let units = MeasurementUnits(rawValue: raw) else { return .lbsOz }
        switch units {
        case .metric:            return .kg
        case .imperialLbDecimal: return .lb
        case .imperialLbOz:      return .lbsOz
        }
    }

    private func setupBmiObservers() {
        manualEntryForm.weight.$value
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.calculateBMI() }
            .store(in: &cancellables)

        $weightUnit
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.calculateBMI() }
            .store(in: &cancellables)
    }

    @MainActor private func calculateBMI() {
        guard isBmiAutoCalculationEnabled else { return }
        guard let weightDouble = Double(manualEntryForm.weight.value), weightDouble > 0 else {
            manualEntryForm.bmi.value = ""
            self.objectWillChange.send()
            return
        }

        let heightString = accountService.activeAccount?.weightHeight ?? "0"
        let storedHeight = ConversionTools.convertStoredHeightToCm(Int(round(Double(heightString) ?? 0)))

        let storedWeight: Double = {
            switch weightUnit {
            case .kg:
                return weightDouble
            case .lb:
                return ConversionTools.convertStoredToKg((ConversionTools.convertDisplayToStored(weightDouble)))
            }
        }()

        let bmiTimesTen = ConversionTools.calculateBMI(weight: storedWeight, height: storedHeight)
        var bmi = Double(bmiTimesTen) / 10.0
        bmi = Double(String(format: "%.1f", bmi)) ?? bmi

        manualEntryForm.bmi.value = bmi == 0 ? "" : bmi > maxBmiValue ? String(99.9) : String(bmi)
        if bmi < maxBmiValue { manualEntryForm.bmi.markAsPristine() }
        manualEntryForm.bmi.validate()

        // Force UI update to ensure MetricInputField reflects the new BMI value immediately
        self.objectWillChange.send()
    }

    private func updateWeightValidators() {
        let maxWeight = self.weightUnit == .kg ? 450.0 : 999.0
        manualEntryForm.weight.removeValidator(ofType: .maxValue)
        // Exclusive cap: a value equal to the max (e.g. exactly 450.0 kg) must be rejected so the
        // "value should be less than 450 kg" message shows, mirroring the lbs path (MOB-1392).
        manualEntryForm.weight.addValidator(Validator.maxValueExclusive(maxWeight))
    }

    private func toTenths(_ string: String) -> Int? {
        guard let value = Double(string) else { return nil }
        return Int(floor(value * 10))
    }

    @MainActor func resetForm() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        stopAutoTimeSync()
        isBmiAutoCalculationEnabled = true
        manualEntryForm = ManualEntryForm()
        manualEntryForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        showMetrics = false
        showDatePicker = false
        showTimePicker = false
        hasUserAdjustedTime = false
        // CRITICAL: Re-initialize observers after reset, otherwise weightUnit changes won't be detected
        initializeObservers()
        setupBmiObservers()
        updateWeightValidators()
        setupDateTimeObservers()
    }

    func saveBPEntry() async {
        guard !isSaving else { return }
        isSaving = true
        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer {
            notificationService.dismissLoader()
            isSaving = false
        }

        guard bpForm.isValid else { return }

        let entryTimestamp = DateTimeTools.isoString(
            date: bpForm.date.value,
            time: bpForm.time.value,
            useUTC: true,
            randomizeSubMinute: true
        )

        let systolic = Double(bpForm.systolic.value) ?? 0
        let diastolic = Double(bpForm.diastolic.value) ?? 0
        let pulse = Double(bpForm.pulse.value) ?? 0
        let meanArterial = String(Int(round(diastolic + (systolic - diastolic) / 3.0)))

        let dto = BpmOperationDTO(
            accountId: nil,
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            meanArterial: meanArterial,
            note: bpForm.notes.value.isEmpty ? nil : bpForm.notes.value,
            source: EntrySource.manual.rawValue,
            unit: nil,
            entryTimestamp: entryTimestamp,
            operationType: nil,
            serverTimestamp: nil
        )

        do {
            logger.log(level: .info, tag: tag, message: "BPM entry save started. timestamp=\(entryTimestamp)")
            try await entryService.createBpmEntry(dto)
            resetBPForm()
            notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
            logger.log(level: .success, tag: tag, message: "BPM entry save succeeded. timestamp=\(entryTimestamp)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save BPM entry. error=\(error.localizedDescription)")
            notificationService.showToast(ToastModel(title: toastLang.errorSavingEntry, message: toastLang.pleaseTryAgain))
        }
    }

    // swiftlint:disable:next function_body_length
    func saveBabyEntry() async {
        guard !isSaving else { return }
        isSaving = true
        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer {
            notificationService.dismissLoader()
            isSaving = false
        }

        guard isBabyFormValid else { return }

        guard case .baby(let profile) = productTypeStore.selectedItem,
              !profile.isPendingSelection else { return }

        let entryTimestamp = DateTimeTools.isoString(
            date: babyForm.date.value,
            time: babyForm.time.value,
            useUTC: true,
            randomizeSubMinute: true
        )

        let weight: Int
        switch babyWeightUnit {
        case .kg:
            let kgValue = Double(babyForm.kg.value) ?? 0
            weight = ConversionTools.convertBabyKgToDecigrams(kgValue)
        case .lb:
            let lbValue = Double(babyForm.lb.value) ?? 0
            weight = ConversionTools.convertBabyLbToDecigrams(lbValue)
        case .lbsOz:
            let pounds = Int(Double(babyForm.pounds.value) ?? 0)
            let ounces = Double(babyForm.ounces.value) ?? 0
            weight = ConversionTools.convertBabyLbsOzToDecigrams(lbs: pounds, oz: ounces)
        }

        let length: Int
        switch babyLengthUnit {
        case .cm:
            let cmValue = Double(babyForm.cm.value) ?? 0
            length = ConversionTools.convertBabyCmToMm(cmValue)
        case .inches:
            let inchesValue = Double(babyForm.inches.value) ?? 0
            length = ConversionTools.convertBabyInchesToMm(inchesValue)
        }

        // A baby entry needs at least one measurement; the unified request builder emits a
        // weight row and/or a length row depending on which is > 0 (MOB-1172).
        guard weight > 0 || length > 0 else {
            logger.log(level: .info, tag: tag, message: "Baby entry save skipped: weight and length are both zero")
            return
        }

        let note = babyForm.notes.value

        do {
            logger.log(level: .info, tag: tag, message: "Baby entry save started. babyId=\(profile.id), timestamp=\(entryTimestamp)")
            try await entryService.createBabyEntry(
                babyId: profile.id,
                weight: weight,
                length: length,
                note: note,
                entryTimestamp: entryTimestamp
            )
            resetBabyForm()
            notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
            logger.log(level: .success, tag: tag, message: "Baby entry save succeeded. babyId=\(profile.id), timestamp=\(entryTimestamp)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save baby entry. error=\(error.localizedDescription)")
            notificationService.showToast(ToastModel(title: toastLang.errorSavingEntry, message: toastLang.pleaseTryAgain))
        }
    }

    func getBPError<T>(for control: FormControl<T>) -> String? {
        bpForm.getError(for: control)
    }

    func getBPWarning<T>(for control: FormControl<T>) -> String? {
        bpForm.getWarning(for: control)
    }

    @MainActor func resetBPForm() {
        bpForm = BloodPressureEntryForm()
        bpForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    func getBabyError<T>(for control: FormControl<T>) -> String? {
        babyForm.getError(for: control)
    }

    var babyWeightError: String? {
        switch babyWeightUnit {
        case .kg: return babyForm.weightErrorMetric
        case .lb: return babyForm.weightErrorLb
        case .lbsOz: return babyForm.weightError
        }
    }

    var babyLengthError: String? {
        switch babyLengthUnit {
        case .cm: return babyForm.lengthErrorCm
        case .inches: return babyForm.lengthError
        }
    }

    @MainActor func resetBabyForm() {
        let newBabyWeightUnit = babyWeightUnitFor(accountService.activeAccount)
        babyWeightUnit = newBabyWeightUnit
        babyLengthUnit = newBabyWeightUnit == .kg ? .cm : .inches
        babyForm = BabyEntryForm()
        babyForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    @MainActor func resetWeightForm() {
        isBmiAutoCalculationEnabled = true
        manualEntryForm = ManualEntryForm()
        manualEntryForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        showMetrics = false
        showDatePicker = false
        showTimePicker = false
        hasUserAdjustedTime = false
        updateWeightValidators()
    }

    func showExitAlert(onConfirm: @escaping () -> Void, onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { _ in
                    self.resetForm()
                    onConfirm()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func confirmDiscardChanges() async -> Bool {
        await withCheckedContinuation { continuation in
            showExitAlert(onConfirm: {
                self.resetForm()
                continuation.resume(returning: true)
            }, onCancel: {
                continuation.resume(returning: false)
            })
        }
    }

    private func assignPercent(_ source: String?, to control: FormControl<String>) {
        guard let value = source?.replacingOccurrences(of: "%", with: "") else { return }
        control.value = value
        control.validate()
    }

    // MARK: - Auto time sync
    func startAutoTimeSync(intervalSeconds: TimeInterval = 1) {
        isTimeSyncActive = true
        timeSyncCancellable?.cancel()
        // Immediate tick to minimize staleness when entering screen/tab
        performAutoTimeTick()
        timeSyncCancellable = Timer
            .publish(every: intervalSeconds, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.performAutoTimeTick()
            }
    }

    func stopAutoTimeSync() {
        isTimeSyncActive = false
        timeSyncCancellable?.cancel()
        timeSyncCancellable = nil
    }

    @MainActor private func performAutoTimeTick() {
        guard isTimeSyncActive else { return }
        // Only auto-update when selected date is today, picker is not open, and user hasn't adjusted time
        guard Calendar.current.isDateInToday(manualEntryForm.date.value) else { return }
        guard !showTimePicker else { return }
        guard !hasUserAdjustedTime else { return }

        let now = Date()
        let calendar = Calendar.current
        // Round down to the minute to avoid jitter due to seconds
        let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        let nowRounded = calendar.date(from: comps) ?? now

        // Respect clamping logic; ensure not ahead of current moment
        let clamped = min(nowRounded, maxSelectableTime)
        if manualEntryForm.time.value != clamped {
            manualEntryForm.time.value = clamped
            manualEntryForm.time.markAsPristine()
            // Ensure SwiftUI sees the nested change
            self.objectWillChange.send()
        }
    }
}
