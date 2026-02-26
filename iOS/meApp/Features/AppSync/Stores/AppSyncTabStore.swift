//
//  AppSyncTabStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import AppSyncPackage
import Combine
import Foundation
import SwiftUI

/// Store responsible for handling AppSync scan results and presenting the
/// confirmation card with **Save** / **Edit** actions.
@MainActor
final class AppSyncTabStore: ObservableObject {
    // MARK: – Dependencies

    @Injector var accountService: AccountService
    @Injector var notificationHelperService: NotificationHelperService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    private let toastLang = ToastStrings.self
    private let loaderLang = LoaderStrings.self
    private let tag = "AppSyncTabStore"

    // Keep strong reference to cancellables if needed in future
    private var cancellables = Set<AnyCancellable>()

    // Holds last scanned raw data so Save/Edit actions have access.
    private var lastScannedData: AppSyncEntryMetrics?

    private enum ScanIgnoreReason: String {
        case invalidWeight = "invalid_weight"
        case outOfRange = "out_of_range"
    }

    private static let minWeightKg: Float = 1.0
    private static let maxWeightKg: Float = 450.0

    /// Converts the scanned body-composition data into the format expected by
    /// `AppSyncEntryCardView` and shows the confirmation modal.
    /// - Parameter data: The `BodyCompData` coming from `AppSyncScannerView`.
    func handleScanned(_ data: BodyCompData, tabViewModel: BottomTabBarViewModel) {
        logger.log(level: .info, tag: tag, message: "AppSync scan received")
        // Determine preferred unit (kg / lbs) based on active account.
        let isMetric = accountService.activeAccount?.weightSettings?.weightUnit == .kg

        if let reason = validateScan(data) {
            logger.log(
                level: .error,
                tag: tag,
                message: "scan_ignored reason=\(reason.rawValue) weight=\(data.weight)"
            )
            notificationHelperService.showToast(
                ToastModel(
                    title: toastLang.somethingWentWrongTitle,
                    message: toastLang.somethingWentWrong
                )
            )
            return
        }

        // Stored *tenths-lbs* value used across the codebase.
        let storedWeight = ConversionTools.convertAppsyncDisplayToStored(Double(data.weight))

        // Optional body-composition metrics → stored tenths (or nil if the value is 0).
        func toTenths(_ value: Float) -> Int? {
            value > 0 ? Int(round(Double(value) * 10)) : nil
        }

        let storedBodyFat = toTenths(data.fat)
        let storedMuscleMass = toTenths(data.muscle)
        let storedWaterWeight = toTenths(data.water)

        // BMI calculation needs user height (stored in *tenths-inches*).
        let heightString = accountService.activeAccount?.weightSettings?.height ?? "0"
        let storedHeightCm = ConversionTools.convertStoredHeightToCm(Int(round(Double(heightString) ?? 0)))
        let storedBMI = ConversionTools.calculateBMI(weight: Double(data.weight), height: storedHeightCm)

        // Build view-model consumed by the confirmation card **and** persist raw data for Save/Edit
        let metrics = AppSyncEntryMetrics(
            storedWeight: storedWeight,
            storedBMI: storedBMI,
            storedBodyFat: storedBodyFat,
            storedWaterWeight: storedWaterWeight,
            storedMuscleMass: storedMuscleMass,
            isMetric: isMetric,
            rawDisplayWeightKg: Double(data.weight)
        )

        // Persist for Save/Edit actions
        lastScannedData = metrics
        logger.log(
            level: .info,
            tag: tag,
            message: "AppSync scan parsed successfully. hasWeight=\(storedWeight > 0), hasBMI=\(storedBMI != nil), isMetric=\(isMetric)"
        )
        // Present confirmation modal.
        showConfirmationModal(metrics: metrics, tabViewModel: tabViewModel)
    }

    // MARK: – Private helpers

    private func showConfirmationModal(metrics: AppSyncEntryMetrics, tabViewModel: BottomTabBarViewModel) {
        logger.log(level: .info, tag: tag, message: "Presenting AppSync confirmation modal. isMetric=\(metrics.isMetric)")
        let modal = AppSyncEntryCardView(
            metrics: metrics,
            onSave: { [weak self] in
                self?.logger.log(level: .info, tag: self?.tag ?? "AppSyncTabStore", message: "AppSync confirmation action selected: save")
                tabViewModel.selectedTab = .dash
                self?.notificationHelperService.dismissModal()
                Task { await self?.saveScannedEntry() }
            },
            onEdit: { [weak self] in
                self?.logger.log(level: .info, tag: self?.tag ?? "AppSyncTabStore", message: "AppSync confirmation action selected: edit")
                // Pre-populate Manual Entry tab with scanned metrics
                tabViewModel.pendingAppSyncEditMetrics = metrics
                tabViewModel.selectedTab = .entry
                self?.notificationHelperService.dismissModal()
            }
        )

        notificationHelperService.showModal(
            ModalData(presentedView: AnyView(modal), backdropDismiss: false)
        )
    }

    // MARK: – Save logic

    private func saveScannedEntry() async {
        guard let data = lastScannedData else {
            logger.log(level: .error, tag: tag, message: "AppSync save aborted: missing scanned data")
            return
        }
        guard let accountId = accountService.activeAccount?.accountId else {
            logger.log(level: .error, tag: tag, message: "AppSync save aborted: no active account")
            return
        }
        logger.log(
            level: .info,
            tag: tag,
            message: """
            AppSync save started. accountId=\(accountId), storedWeight=\(data.storedWeight), \
            hasBodyFat=\(data.storedBodyFat != nil), hasMuscle=\(data.storedMuscleMass != nil), \
            hasWater=\(data.storedWaterWeight != nil)
            """
        )

        let entryTimestamp = DateTimeTools.getCurrentDatetimeIsoString()

        // Build DB models
        let scaleEntry = BathScaleEntry(
            weight: data.storedWeight,
            bodyFat: data.storedBodyFat,
            muscleMass: data.storedMuscleMass,
            water: data.storedWaterWeight,
            bmi: data.storedBMI,
            source: EntrySource.appsyncScale.rawValue
        )

        let unit = data.isMetric ? WeightUnit.kg.rawValue : WeightUnit.lb.rawValue
        let scaleMetric = BathScaleMetric(unit: unit)

        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue,
            isSynced: false
        )
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric

        notificationHelperService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        do {
            try await entryService.saveNewEntry(entry)
            logger.log(level: .success, tag: tag, message: "AppSync save succeeded. accountId=\(accountId), entryTimestamp=\(entryTimestamp)")
            notificationHelperService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
        } catch {
            logger.log(level: .error, tag: tag, message: "AppSync save failed. accountId=\(accountId)", data: error.localizedDescription)
            notificationHelperService.showToast(ToastModel(title: toastLang.errorSavingEntry, message: toastLang.pleaseTryAgain))
        }
        notificationHelperService.dismissLoader()
        notificationHelperService.dismissModal()
    }

    private func validateScan(_ data: BodyCompData) -> ScanIgnoreReason? {
        if data.weight <= 0 || data.weight.isNaN || data.weight.isInfinite {
            return .invalidWeight
        }

        if data.weight < Self.minWeightKg || data.weight > Self.maxWeightKg {
            return .outOfRange
        }

        return nil
    }
}
