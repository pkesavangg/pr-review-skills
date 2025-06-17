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
        initializeObservers()
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
        // TODO: Implement the logic to save the entry using `EntryService`.
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
            }
            .store(in: &cancellables)
    }

    private func updateWeightValidators() {
        let maxWeight = self.weightUnit == .kg ? 450.0 : 999.0

        manualEntryForm.weight.removeValidator(ofType: .maxValue)
        manualEntryForm.weight.addValidator(Validator.maxValue(maxWeight))
    }
} 
