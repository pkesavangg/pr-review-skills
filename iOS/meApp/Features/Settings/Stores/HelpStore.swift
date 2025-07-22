//
//  HelpStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import Foundation
import Combine
import SwiftUI
import StoreKit
import UIKit

// MARK: - Settings Store
/// A store to manage user settings and account actions.
@MainActor
class HelpStore: ObservableObject {
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    @Injector var feedService: FeedService
    @Injector var scaleService: ScaleService
    var kvStorage = KvStorageService.shared
    var theme = Theme.shared
    
    @Published var activeAccount: Account?
    
    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL? = nil
    // NEW – debug-menu state
    @Published var showDebugMenu = false
    
    // MARK: - Scale Log State
    @Published var showScaleLogSheet = false
    @Published var scales: [Device] = []
    
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
    
    init() {
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                self?.scales = scales.filter({$0.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue})
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
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingLogs))
            do {
                try await logger.sendLogsToServer()
                notificationService.showToast(ToastModel(message: toastLang.logsSent))
            } catch {
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
                notificationService.showLoader(LoaderModel(text: loaderLang.resync))
                
                do {
                    // Clear local entries and sync timestamp
                    await entryService.clearAllData()
                    try await entryService.clearLastSyncTimestamp()
                    
                    // Resync with server
                    await entryService.syncAllEntriesWithRemote()
                    // Show success toast after a delay
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
                showErrorToast()
            }
        }
    }
    
    /// Clears all local persistence (dangerous!).
    func clearAllLocalData() {
        Task {
            let alertLang = AlertStrings.DataClearingAlert.self
            
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
        logger.log(level: .info, tag: tag, message: "Show Rate Modal tapped")
        // iOS: Request review prompt if available.
        AppRatingHelper.requestReview()
    }
    
    /// Sends scale-specific logs.
    /// Sends scale-specific logs.
    func sendScaleLogHandler(broadcastId: String? = nil) {
        let resolvedBroadcastId: String? = {
            if let id = broadcastId {
                return id
            } else if scales.count == 1 {
                return scales.first?.broadcastIdString
            } else {
                return nil
            }
        }()

        if let idToSend = resolvedBroadcastId {
            sendScaleLogsToServer(broadcastId: idToSend)
        } else {
            showScaleLogSheet = true
        }
    }

    private func sendScaleLogsToServer(broadcastId: String) {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingLogs))
            
            do {
                // TODO: Implement actual scale log sending
                // await exportService.sendScaleLog(broadcastId)
                try await Task.sleep(for: .seconds(3)) // Simulate API call
                notificationService.showToast(ToastModel(message: toastLang.logsSent))
                showScaleLogSheet = false // Hide sheet after sending
            } catch {
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
    
    deinit {
        cancellables.forEach { $0.cancel() }
    }
}

