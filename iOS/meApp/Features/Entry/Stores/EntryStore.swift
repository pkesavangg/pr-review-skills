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

    // Localization strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self

    // Form
    @Published var manualEntryForm = ManualEntryForm()

    // UI State
    @Published var weightUnit: WeightUnit = .lb
    @Published var canShowOtherBodyMetrics = false

    // Maximum BMI that can be set automatically (matches web)
    private let maxBmiValue: Double = 99.0

    private var cancellables = Set<AnyCancellable>()
    
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
        
        initializeObservers()
        setupBmiObservers()
        updateWeightValidators()
        // TODO: Update the canShowOtherBodyMetrics based on 0412 availability in the paired scales.
        canShowOtherBodyMetrics = true
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

        // TODO: Need to handle the api calls
        print("entryTimestamp: \(entryTimestamp)")
        print("weight: \(weightStored)")
        print("bodyFat: \(String(describing: bodyFat)) muscleMass: \(String(describing: muscleMass)) water: \(String(describing: water)) bmi: \(String(describing: bmi)) boneMass: \(String(describing: boneMass)) bmr: \(String(describing: bmr)) metabolicAge: \(String(describing: metabolicAge)) proteinPercent: \(String(describing: proteinPercent)) pulse: \(String(describing: pulse)) skeletalMusclePercent: \(String(describing: skeletalMusclePercent)) subcutaneousFatPercent: \(String(describing: subcutaneousFatPercent)) visceralFatLevel: \(String(describing: visceralFatLevel)) unit: \(unit)")
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
            let storedHeight = ConversionTools.convertStoredHeightToCm(Int(heightString) ?? 0)
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
            self.manualEntryForm.bmi.value = bmi == 0 ? "" : bmi > self.maxBmiValue ? String(99.9) : String(bmi)
            print("Calculated BMI: \(bmi)", String(bmi), self.manualEntryForm.bmi.value)

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
} 
