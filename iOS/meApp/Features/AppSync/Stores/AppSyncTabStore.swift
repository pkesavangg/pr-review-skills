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

@MainActor
protocol AppSyncTabRouting: AnyObject {
    var selectedTab: BottomTab { get set }
    var pendingAppSyncEditMetrics: AppSyncEntryMetrics? { get set }
}

extension BottomTabBarViewModel: AppSyncTabRouting {}

/// Store responsible for handling AppSync scan results and presenting the
/// confirmation card with **Save** / **Edit** actions.
@MainActor
final class AppSyncTabStore: ObservableObject {
    // MARK: – Dependencies
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationHelperService: NotificationHelperServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var kvStorage: KvStorageServiceProtocol
    private let toastLang = ToastStrings.self
    private let loaderLang = LoaderStrings.self
    private let tag = "AppSyncTabStore"

    // Keep strong reference to cancellables if needed in future
    private var cancellables = Set<AnyCancellable>()

    // Holds last scanned raw data so Save/Edit actions have access.
    private var lastScannedData: AppSyncEntryMetrics?

    /// MA-3863: last-used camera zoom for the active account, restored from
    /// KvStorage before each scan session so the camera reopens at the same zoom.
    @Published var initialZoom: CGFloat?

    private enum ScanIgnoreReason: String {
        case invalidWeight = "invalid_weight"
        case outOfRange = "out_of_range"
    }

    private static let minWeightKg: Float = 1.0
    private static let maxWeightKg: Float = 450.0

    init() {
        // Resolve once per store instance to avoid cross-test DI races when
        // async save/edit actions execute after other suites reset the container.
        _ = accountService
        _ = notificationHelperService
        _ = entryService
        _ = logger
        _ = kvStorage
    }

    /// MA-3863: restore the saved zoom level for the active account before a scan
    /// session. Falls back to nil (scanner's own default) when none is stored.
    func loadSavedZoom() {
        guard let accountId = accountService.activeAccount?.accountId else {
            initialZoom = nil
            return
        }
        let zoomMap = kvStorage.getCodable(forKey: KvStorageKeys.appSyncCameraZoomMap.rawValue, as: [String: Double].self)
        initialZoom = zoomMap?[accountId].map { CGFloat($0) }
    }

    /// MA-3863: persist the last-used zoom level per account.
    private func saveZoom(_ zoom: Float, for accountId: String) {
        var zoomMap = kvStorage.getCodable(forKey: KvStorageKeys.appSyncCameraZoomMap.rawValue, as: [String: Double].self) ?? [:]
        let newZoom = Double(zoom)
        guard zoomMap[accountId] != newZoom else { return }
        zoomMap[accountId] = newZoom
        kvStorage.setCodable(zoomMap, forKey: KvStorageKeys.appSyncCameraZoomMap.rawValue)
    }

    /// Converts the scanned body-composition data into the format expected by
    /// `AppSyncEntryCardView` and shows the confirmation modal.
    /// - Parameter data: The `BodyCompData` coming from `AppSyncScannerView`.
    func handleScanned(_ data: BodyCompData, tabViewModel: BottomTabBarViewModel) {
        // MA-3863: persist the zoom level used for this scan so the camera reopens
        // at the same zoom next time for this account.
        if let accountId = accountService.activeAccount?.accountId, data.zoomLevel > 0 {
            saveZoom(data.zoomLevel, for: accountId)
            initialZoom = CGFloat(data.zoomLevel)
        }
        handleScanned(
            weightKg: data.weight,
            fat: data.fat,
            muscle: data.muscle,
            water: data.water,
            tabRouter: tabViewModel
        )
    }

    func handleScanned(
        weightKg: Float,
        fat: Float,
        muscle: Float,
        water: Float,
        tabRouter: AppSyncTabRouting
    ) {
        logger.log(level: .info, tag: tag, message: "AppSync scan received")
        // Determine preferred unit (kg / lbs) based on active account.
        let isMetric = accountService.activeAccount?.weightUnit == .kg

        if let reason = validateScan(weightKg: weightKg) {
            logger.log(
                level: .error,
                tag: tag,
                message: "scan_ignored reason=\(reason.rawValue) weight=\(weightKg)"
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
        let storedWeight = ConversionTools.convertAppsyncDisplayToStored(Double(weightKg))

        // Optional body-composition metrics → stored tenths (or nil if the value is 0).
        func toTenths(_ value: Float) -> Int? {
            value > 0 ? Int(round(Double(value) * 10)) : nil
        }

        let storedBodyFat = toTenths(fat)
        let storedMuscleMass = toTenths(muscle)
        let storedWaterWeight = toTenths(water)

        // BMI calculation needs user height (stored in *tenths-inches*).
        let heightString = accountService.activeAccount?.weightHeight ?? "0"
        let storedHeightCm = ConversionTools.convertStoredHeightToCm(Int(round(Double(heightString) ?? 0)))
        let storedBMI = ConversionTools.calculateBMI(weight: Double(weightKg), height: storedHeightCm)

        // Build view-model consumed by the confirmation card **and** persist raw data for Save/Edit
        let metrics = AppSyncEntryMetrics(
            storedWeight: storedWeight,
            storedBMI: storedBMI,
            storedBodyFat: storedBodyFat,
            storedWaterWeight: storedWaterWeight,
            storedMuscleMass: storedMuscleMass,
            isMetric: isMetric,
            rawDisplayWeightKg: Double(weightKg)
        )

        // Persist for Save/Edit actions
        lastScannedData = metrics
        logger.log(
            level: .info,
            tag: tag,
            message: "AppSync scan parsed successfully. hasWeight=\(storedWeight > 0), hasBMI=\(storedBMI), isMetric=\(isMetric)"
        )
        // Present confirmation modal.
        showConfirmationModal(metrics: metrics, tabRouter: tabRouter)
    }

    // MARK: – Private helpers

    private func showConfirmationModal(metrics: AppSyncEntryMetrics, tabRouter: AppSyncTabRouting) {
        logger.log(level: .info, tag: tag, message: "Presenting AppSync confirmation modal. isMetric=\(metrics.isMetric)")
        let modal = AppSyncEntryCardView(
            metrics: metrics,
            onSave: { [weak self] in
                self?.logger.log(level: .info, tag: self?.tag ?? "AppSyncTabStore", message: "AppSync confirmation action selected: save")
                self?.handleSaveAction(tabRouter: tabRouter)
            },
            onEdit: { [weak self] in
                self?.logger.log(level: .info, tag: self?.tag ?? "AppSyncTabStore", message: "AppSync confirmation action selected: edit")
                self?.handleEditAction(metrics: metrics, tabRouter: tabRouter)
            }
        )

        notificationHelperService.showModal(
            ModalData(presentedView: AnyView(modal), backdropDismiss: false)
        )
    }

    // MARK: – Save logic

    func handleSaveAction(tabRouter: AppSyncTabRouting) {
        tabRouter.selectedTab = .dash
        notificationHelperService.dismissModal()
        Task { [self] in
            await saveScannedEntry()
        }
    }

    func handleEditAction(metrics: AppSyncEntryMetrics, tabRouter: AppSyncTabRouting) {
        // Pre-populate Manual Entry tab with scanned metrics
        tabRouter.pendingAppSyncEditMetrics = metrics
        tabRouter.selectedTab = .entry
        notificationHelperService.dismissModal()
    }

    func saveScannedEntry() async {
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
            entryType: EntryType.scale.rawValue,
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

    private func validateScan(weightKg: Float) -> ScanIgnoreReason? {
        if weightKg <= 0 || weightKg.isNaN || weightKg.isInfinite {
            return .invalidWeight
        }

        if weightKg < Self.minWeightKg || weightKg > Self.maxWeightKg {
            return .outOfRange
        }

        return nil
    }
}
