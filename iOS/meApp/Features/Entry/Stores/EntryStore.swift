//  EntryStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import Foundation
import SwiftUI
import Combine

/// Store responsible for handling manual weight entry flow.
@MainActor
final class EntryStore: ObservableObject {
    // Dependencies
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    @Injector var scaleService: ScaleService
    
    // Localization strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let alertLang = AlertStrings.ManualEntryExitAlert.self
    private let loaderLang = LoaderStrings.self
    
    // Form
    @Published var manualEntryForm = ManualEntryForm()
    
    // UI State
    @Published var weightUnit: WeightUnit = .lb
    @Published var canShowOtherBodyMetrics = false
    @Published var showMetrics = false
    @Published var showDatePicker = false
    @Published var showTimePicker = false
    
    // Maximum BMI that can be set automatically (matches web)
    private let maxBmiValue: Double = 99.0
    private var cancellables = Set<AnyCancellable>()
    
    let tag = "EntryStore"
    
    var maxSelectableTime: Date {
        // If selected date is today, cap at current time; otherwise end of day
        if Calendar.current.isDateInToday(manualEntryForm.date.value) {
            return Date()
        } else {
            var comps = Calendar.current.dateComponents([.year, .month, .day], from: manualEntryForm.date.value)
            comps.hour = 23; comps.minute = 59
            return Calendar.current.date(from: comps) ?? Date()
        }
    }
    
    // MARK: - Init
    init() {
        // Forward form updates so that SwiftUI refreshes when any control changes
        manualEntryForm.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
        
        // Update canShowOtherBodyMetrics based on btWifiR4 scale availability
        scaleService.$scales
            .map { scales in
                scales.contains { $0.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue }
            }
            .receive(on: DispatchQueue.main)
            .assign(to: \.canShowOtherBodyMetrics, on: self)
            .store(in: &cancellables)
        
        initializeObservers()
        setupBmiObservers()
        updateWeightValidators()
    }
    
    // MARK: - Public helpers
    
    /// Retrieves error message for a given form control (convenience wrapper).
    func getError<T>(for control: FormControl<T>) -> String? {
        manualEntryForm.getError(for: control, weightUnit: weightUnit)
    }
    
    /// Persists the manual entry using `EntryService`.
    func saveEntry() async {
        guard manualEntryForm.isValid else { return }
        // Build ISO-8601 timestamp with selected date + time
        let entryTimestamp = DateTimeTools.isoString(date: manualEntryForm.date.value,
                                                     time: manualEntryForm.time.value,
                                                     useUTC: true,
                                                     randomizeSubMinute: true)
        
        let weightStored = ConversionTools.convertDisplayToStored(Double(manualEntryForm.weight.value) ?? 0,
                                                                  forceMetric: false,
                                                                  isMetric: weightUnit == .kg)
        // Format optional body metrics into *tenths* (int) where required
        let bodyFat    = toTenths(manualEntryForm.bodyFat.value)
        let muscleMass = toTenths(manualEntryForm.muscleMass.value)
        let water      = toTenths(manualEntryForm.bodyWater.value)
        let bmi        = toTenths(manualEntryForm.bmi.value)
        let boneMass   = toTenths(manualEntryForm.boneMass.value)
        let bmr        = toTenths(manualEntryForm.bmr.value)
        let metabolicAge = Int(manualEntryForm.metabolicAge.value) // whole number
        let proteinPercent = toTenths(manualEntryForm.protein.value)
        let pulse = Int(manualEntryForm.heartRate.value)
        let skeletalMusclePercent = toTenths(manualEntryForm.skeletalMuscles.value)
        let subcutaneousFatPercent = toTenths(manualEntryForm.subcutaneousFat.value)
        let visceralFatLevel = toTenths(manualEntryForm.visceralFat.value)
        let unit = weightUnit == .kg ? WeightUnit.kg.rawValue : WeightUnit.lb.rawValue
        
        // Build DB models
        guard let accountId = accountService.activeAccount?.accountId else { return }
        
        let scaleEntry = BathScaleEntry(
            weight: weightStored,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: EntrySource.manual.rawValue,
        )
        
        let scaleMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass,
            impedance: nil,
            unit: unit
        )
        
        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue,
            isSynced: false
        )
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric
        
        
        notificationService.showLoader(LoaderModel(
            text: loaderLang.savingEntry,
        ))
        do {
            try await entryService.saveNewEntry(entry)
            // Reset form after successful save so fields are pristine
            resetForm()
            notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
        } catch {
            logger.log(level: .error, tag: self.tag, message: "Failed to save manual entry", data: error)
            notificationService.showToast(ToastModel(title: toastLang.errorSavingEntry, message: toastLang.pleaseTryAgain))
        }
        notificationService.dismissLoader()
    }
    
    // MARK: - AppSync Edit Helpers
    /// Populates the manual entry form with values coming from an AppSync scan so the
    /// user can review and adjust before saving.
    /// - Parameter metrics: The scanned body-composition metrics captured on the AppSync tab.
    func populateFromAppSync(metrics: AppSyncEntryMetrics) {
        // Update unit preference first so validation is correct before assigning values.
        weightUnit = metrics.isMetric ? .kg : .lb
        updateWeightValidators()
        
        // Weight
        let displayWeight = ConversionTools.convertStoredToDisplay(metrics.storedWeight, isMetric: metrics.isMetric)
        manualEntryForm.weight.value = String(format: "%.1f", displayWeight)
        manualEntryForm.weight.validate()
        
        // BMI – already a plain decimal string in `metrics.bmi`
        if let bmiStr = metrics.bmi {
            manualEntryForm.bmi.value = bmiStr
            manualEntryForm.bmi.validate()
        }
        
        assignPercent(metrics.bodyFat, to: manualEntryForm.bodyFat)
        assignPercent(metrics.muscleMass, to: manualEntryForm.muscleMass)
        assignPercent(metrics.waterWeight, to: manualEntryForm.bodyWater)
        
        // Expand metrics accordion so the user sees the pre-filled values
        showMetrics = true
    }
    
    // MARK: - Private helpers
    
    private func initializeObservers() {
        accountService.$activeAccount
            .sink { [weak self] data in
                guard let self = self else { return }
                if let unit = data?.weightSettings?.weightUnit {
                    self.weightUnit = unit
                    self.updateWeightValidators()
                } else {
                    self.weightUnit = .lb
                }
                // Re-calculate BMI when account changes (height may be different)
                self.calculateBMI()
            }
            .store(in: &cancellables)
    }
    
    /// Observers that trigger BMI recalculation when relevant fields change.
    private func setupBmiObservers() {
        // Weight field changes
        manualEntryForm.weight.$value
            .sink { [weak self] _ in self?.calculateBMI() }
            .store(in: &cancellables)
        // Weight unit changes (kg / lb)
        $weightUnit
            .sink { [weak self] _ in self?.calculateBMI() }
            .store(in: &cancellables)
    }
    
    /// Calculates BMI using the same logic as the web implementation and
    /// writes the result back into the form when the BMI field is pristine.
    private func calculateBMI() {
        DispatchQueue.main.async {
            // Only calculate if the BMI field is pristine (not dirty)
            guard self.manualEntryForm.bmi.isPristine else { return }
            guard let weightDouble = Double(self.manualEntryForm.weight.value), weightDouble > 0 else { return }
            
            let heightString = self.accountService.activeAccount?.weightSettings?.height ?? "0"
            let storedHeight = ConversionTools.convertStoredHeightToCm(Int(round(Double(heightString) ?? 0)))
            // Convert weight -> stored (tenths of lbs) depending on unit
            let storedWeight: Double = {
                switch self.weightUnit {
                case .kg:
                    return weightDouble
                case .lb:
                    return ConversionTools.convertStoredToKg((ConversionTools.convertDisplayToStored(weightDouble)))
                }
            }()
            
            // Calculate BMI * 10 (single-decimal precision) using conversion utility
            let bmiTimesTen = ConversionTools.calculateBMI(weight: storedWeight, height: storedHeight)
            var bmi = Double(bmiTimesTen) / 10.0
            
            // Round to 1 decimal place
            bmi = Double(String(format: "%.1f", bmi)) ?? bmi
            // If BMI is NaN or negative, set to 0
            // If BMI exceeds max value, set to 99.9 (To show the error state)
            self.manualEntryForm.bmi.value = bmi == 0 ? "" : bmi > self.maxBmiValue ? String(99.9) : String(bmi)
            if bmi < self.maxBmiValue {
                self.manualEntryForm.bmi.markAsPristine()
            }
            self.manualEntryForm.bmi.validate()
        }
    }
    
    private func updateWeightValidators() {
        let maxWeight = self.weightUnit == .kg ? 450.0 : 999.0
        
        manualEntryForm.weight.removeValidator(ofType: .maxValue)
        manualEntryForm.weight.addValidator(Validator.maxValue(maxWeight))
    }
    
    /// Converts a user-entered metric string (e.g. "13.5") into stored *tenths* Int (e.g. 135).
    /// Returns `nil` if the string cannot be parsed.
    private func toTenths(_ string: String) -> Int? {
        guard let value = Double(string) else { return nil }
        return Int(floor(value * 10))
    }
    
    // MARK: Exit handling helpers
    /// Resets the current form to pristine state (used when discarding unsaved changes).
    func resetForm() {
        // Cancel existing subscriptions so we don't leak
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        // Replace with a brand-new form instance
        manualEntryForm = ManualEntryForm()
        // Re-wire observers that depend on the new form instance
        manualEntryForm.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
        showMetrics = false
        showDatePicker = false
        showTimePicker = false
        setupBmiObservers()
        updateWeightValidators()
    }
    
    /// Presents an exit confirmation alert when the form has unsaved changes.
    /// - Parameters:
    ///   - onConfirm: Executed when user confirms discarding changes.
    ///   - onCancel:  Executed when user chooses to stay (optional).
    func showExitAlert(onConfirm: @escaping () -> Void,
                       onCancel: (() -> Void)? = nil) {
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
    
    /// Async wrapper around `showExitAlert` that suspends until the user makes
    /// a decision.
    /// - Returns: `true` if the user confirms discarding changes, `false`
    ///   otherwise.
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
    
    // Helper to strip trailing "%" and assign to field
    private func assignPercent(_ source: String?, to control: FormControl<String>) {
        guard let value = source?.replacingOccurrences(of: "%", with: "") else { return }
        control.value = value
        control.validate()
    }
}
