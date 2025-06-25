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
    
    var theme = Theme.shared
    
    @Published var activeAccount: Account?

    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL? = nil
    // NEW – debug-menu state
    @Published var showDebugMenu = false
    
    // ───────────────────────────────
    //  Five-tap detection (private)
    // ───────────────────────────────
    private var headerTapCounter = 0
    private var firstTapTime: Date?

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
        logger.log(level: .info, tag: "DebugMenu", message: "Send Weight Gurus Log tapped")
        // TODO: Hook into actual log exporter service when available.
        notificationService.showToast(ToastModel(message: "Logs sent."))
    }

    /// Triggers a resync of all entries with the server.
    func resyncEntries() {
        logger.log(level: .info, tag: "DebugMenu", message: "Resync Entries tapped")
        // TODO: Implement real resync using entryService when backend ready.
        notificationService.showToast(ToastModel(message: "Resync started."))
    }

    /// Clears all local persistence (dangerous!).
    func clearAllLocalData() {
        logger.log(level: .info, tag: "DebugMenu", message: "Clear Local Data tapped")
        // TODO: Wire into actual local data wiping routine.
        notificationService.showToast(ToastModel(message: "Local data cleared."))
    }

    /// Shows the system/app rating modal.
    func showAppRateModal() {
        logger.log(level: .info, tag: "DebugMenu", message: "Show Rate Modal tapped")
        // iOS: Request review prompt if available.
        AppRatingHelper.requestReview()
    }

    /// Sends scale-specific logs.
    func sendScaleLog() {
        logger.log(level: .info, tag: "DebugMenu", message: "Send Scale Log tapped")
        // TODO: Implement scale log export.
        notificationService.showToast(ToastModel(message: "Scale logs sent."))
    }
}

