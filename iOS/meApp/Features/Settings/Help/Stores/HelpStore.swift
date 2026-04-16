//
//  HelpStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import Combine
import Foundation
import StoreKit
import SwiftUI
import UIKit

// MARK: - Settings Store
/// A store to manage user settings and account actions.
@MainActor
class HelpStore: ObservableObject {
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var feedService: FeedServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    private let appReviewHandler: AppReviewHandlerProtocol
    var kvStorage = KvStorageService.shared
    var theme = Theme.shared
    
    @Published var activeAccount: AccountSnapshot?
    
    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL?
    // NEW – debug-menu state
    @Published var showDebugMenu = false
    
    // MARK: - Scale Log State
    @Published var showScaleLogSheet = false
    @Published var scales: [DeviceSnapshot] = []

    var isSendScaleLogEnabled: Bool {
        if scales.count > 1 {
            return true
        }
        return scales.first?.isConnected == true
    }
    
    var shouldShowScaleTroubleshooting: Bool {
        !scales.isEmpty
    }
    
    var cancellables: Set<AnyCancellable> = []
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    
    // ───────────────────────────────
    //  Five-tap detection (private)
    // ───────────────────────────────
    private var headerTapCounter = 0
    private var firstTapTime: Date?
    private let tag = "HelpStore"
    
    init(appReviewHandler: AppReviewHandlerProtocol? = nil) {
        self.appReviewHandler = appReviewHandler ?? AppReviewService.shared
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                self?.scales = scales.filter { $0.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue }
            }
            .store(in: &cancellables)
    }
    
    /// Presents the in-app browser for the given product SKU.
    func openProductManual(sku: String) {
        guard let url = URL(string: "\(AppConstants.Product.baseURL)/\(sku)") else { return }
        productURL = url
        showProductBrowser = true
    }
    
    /// Call from the view's tap gesture on the header.
    func handleHeaderTap() {
        let now = Date()
        
        if let first = firstTapTime, now.timeIntervalSince(first) < 5 {
            headerTapCounter += 1
        } else {
            // either first tap or taps too slow → restart window
            headerTapCounter = 1
            firstTapTime = now
        }
        
        if headerTapCounter >= 5 {
            // Success – trigger sheet
            headerTapCounter = 0
            firstTapTime = nil
            showDebugMenu = true
        }
    }
    
    /// Resets the flag after the sheet is dismissed (optional helper).
    func dismissDebugMenu() {
        showDebugMenu = false
    }
    
    // MARK: - Debug Menu Actions
    /// Sends Weight Gurus application logs to support.
    func sendWeightGurusLog() {
        Task {
            logger.log(level: .info, tag: tag, message: "Weight Gurus log upload started")
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingLogs))
            do {
                try await logger.sendLogsToServer()
                logger.log(level: .info, tag: tag, message: "Weight Gurus log upload completed successfully")
                notificationService.showToast(ToastModel(message: toastLang.logsSent))
            } catch {
                if case HTTPError.noInternet = error {
                    logger.log(level: .error, tag: tag, message: "Weight Gurus log upload failed: offline")
                }
                logger.log(level: .error, tag: tag, message: "Failed to send logs: \(error.localizedDescription)")
                switch error {
                case HTTPError.noInternet:
                    break // No message needed, handled by NetworkMonitor
                default:
                    notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.restartAndTryAgain))
                }
            }
            notificationService.dismissLoader()
        }
    }
    
    /// Triggers a resync of all entries with the server.
    func resyncEntries() {
        Task {
            let networkStatus = NetworkMonitor.shared.isConnected
            if networkStatus {
                logger.log(level: .info, tag: tag, message: "Entry resync started")
                notificationService.showLoader(LoaderModel(text: loaderLang.resync))
                
                do {
                    // Clear local entries and sync timestamp
                    await entryService.clearAllData()
                    try await entryService.clearLastSyncTimestamp()
                    
                    // Resync with server
                    await entryService.syncAllEntriesWithRemote()
                    // Show success toast after a delay
                    logger.log(level: .info, tag: tag, message: "Entry resync completed successfully")
                    notificationService.showToast(ToastModel(message: toastLang.synced))
                } catch {
                    logger.log(level: .error, tag: tag, message: "Resync failed: \(error.localizedDescription)")
                    notificationService.showToast(ToastModel(
                        title: toastLang.somethingWentWrongTitle,
                        message: toastLang.restartAndTryAgain
                    ))
                }
                notificationService.dismissLoader()
            } else {
                logger.log(level: .error, tag: tag, message: "Entry resync blocked: offline")
                showErrorToast()
            }
        }
    }
    
    /// Clears all local persistence (dangerous!).
    func clearAllLocalData() {
        Task {
            let alertLang = AlertStrings.DataClearingAlert.self
            logger.log(level: .info, tag: tag, message: "Clear all local data started")
            
            // Show loading indicator
            notificationService.showLoader(LoaderModel(text: LoaderStrings.pleaseWait))
            
            do {
                // Clear all data from repositories
                try await Task.sleep(for: .seconds(3)) // Simulate delay for UI
                kvStorage.clearAll()
                await entryService.clearAllData()
                await scaleService.clearAllData()
                try await accountService.deleteAllAccounts()
                // Show success alert
                notificationService.dismissLoader()
                let alert = AlertModel(
                    title: alertLang.successHeader,
                    message: alertLang.successMessage,
                    buttons: [
                        AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
                    ]
                )
                notificationService.showAlert(alert)
                logger.log(level: .info, tag: tag, message: "Clear all local data completed successfully")
                
            } catch {
                // Show error alert
                notificationService.dismissLoader()
                let alert = AlertModel(
                    title: alertLang.errorHeader,
                    message: alertLang.errorMessage,
                    buttons: [
                        AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
                    ]
                )
                notificationService.showAlert(alert)
                logger.log(level: .error, tag: tag, message: "Failed to clear local data: \(error.localizedDescription)")
            }
        }
    }
    
    /// Shows the system/app rating modal.
    func showAppRateModal() {
        logger.log(level: .info, tag: tag, message: "Presenting app rating modal")
        Task {
            await appReviewHandler.triggerAppReview(isFromDebug: true)
        }
    }
    
    /// Sends scale-specific logs.
    func sendScaleLogHandler(device: DeviceSnapshot? = nil) {
        let resolvedDevice: DeviceSnapshot? = {
            if let id = device {
                return id
            } else if scales.count == 1 {
                return scales.first
            } else {
                return nil
            }
        }()

        if let device = resolvedDevice {
            sendScaleLogsToServer(device: device)
        } else {
            showScaleLogSheet = true
        }
    }

    private func sendScaleLogsToServer(device: DeviceSnapshot) {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingLogs))
            
            do {
                let result = await bluetoothService.getDeviceLogs(broadcastId: device.broadcastIdString ?? "")
                switch result {
                case .success(let logs):
                    try await logger.sendScaleLogsToServer(deviceLogs: logs.logs)
                    logger.log(level: .info, tag: tag, message: "Scale logs upload completed successfully")
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to get scale logs: \(error.localizedDescription)")
                    notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.restartAndTryAgain))
                    return
                }
                notificationService.showToast(ToastModel(message: toastLang.logsSent))
                showScaleLogSheet = false // Hide sheet after sending
            } catch {
                if case HTTPError.noInternet = error {
                    logger.log(level: .error, tag: tag, message: "Scale log upload failed: offline")
                }
                logger.log(level: .error, tag: tag, message: "Failed to send scale log: \(error.localizedDescription)")
                switch error {
                case HTTPError.noInternet:
                    break // No message needed, handled by NetworkMonitor
                default:
                    notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.restartAndTryAgain))
                }
            }
            notificationService.dismissLoader()
        }
    }
    
    private func showErrorToast() {
        notificationService.showToast(ToastModel(
            title: toastLang.resyncErrorTitle,
            message: toastLang.resyncError
        ))
    }
    
    func openHelp() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(ModelNumberHelpModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
    }
}
