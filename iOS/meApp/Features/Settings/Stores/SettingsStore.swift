//
//  SettingsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Foundation
import Combine
import SwiftUI

// MARK: - Settings Store
/// A store to manage user settings and account actions.
@MainActor
class SettingsStore: ObservableObject {
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerService
    var theme = Theme.shared
    
    @Published var activeAccount: Account?
    
    var cancellables = Set<AnyCancellable>()
    
    // Localization strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let legalURLs = AppConstants.LegalURLs.self
    
    let tag = "SettingsStore"
    
    // MARK: - In-App Browser State
    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showGreaterGoodsBrowser: Bool = false
    @Published var browserURL: URL? = nil

    /// Main browser presentation binding for the view
    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.showPrivacyBrowser || self.showTermsBrowser || self.showGreaterGoodsBrowser },
            set: { newValue in
                if !newValue {
                    self.showPrivacyBrowser = false
                    self.showTermsBrowser = false
                    self.showGreaterGoodsBrowser = false
                    self.browserURL = nil
                }
            }
        )
    }

    /// Browser URL used by the view
    var presentingBrowserURL: URL {
        browserURL ?? legalURLs.greaterGoodsWebsite
    }
    
    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.activeAccount = account
            }
            .store(in: &accountService.cancellables)
    }
    
    func handleLogout() {
        let logoutAlert = alertLang.LogoutAlert
        let alert = AlertModel(
            title: logoutAlert.title,
            message: logoutAlert.message,
            buttons: [
                AlertButtonModel(title: logoutAlert.logoutButton, type: .primary) { _ in
                    self.logout()
                },
                AlertButtonModel(title: logoutAlert.cancelButton, type: .secondary) { _ in
                    
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func handleDeleteAccount() {
        let deleteAccountAlert = alertLang.DeleteAccountAlert
        let alert = AlertModel(
            title: deleteAccountAlert.title,
            message: deleteAccountAlert.message,
            buttons: [
                AlertButtonModel(title: deleteAccountAlert.deleteButton, type: .primary) { _ in
                    self.deleteAccount()
                },
                AlertButtonModel(title: deleteAccountAlert.cancelButton, type: .secondary) { _ in
                    
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    
    private func logout() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loggingOut ))
            do {
                try await accountService.logOut()
            } catch {
                logger.log(level: .error, tag: tag, message: "Logout failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    private func deleteAccount() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingAccount ))
            do {
                try await accountService.deleteAccount()
                // TODO: Need to clear all the connected scales and integrated entries to health kit and clear appearance choice
            } catch  {
                logger.log(level: .error, tag: tag, message: "Delete account failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Support Link Handlers
    func openPrivacy() {
        browserURL = legalURLs.privacyPolicy
        showPrivacyBrowser = true
    }

    func openTerms() {
        browserURL = legalURLs.termsOfService
        showTermsBrowser = true
    }

    func openHelp() {
        // TODO: Need to handle this
    }

    func openGreaterGoods() {
        browserURL = legalURLs.greaterGoodsWebsite
        showGreaterGoodsBrowser = true
    }

    // MARK: - Computed Profile Info
    var profileInitial: String {
        if let firstInitial = activeAccount?.firstName?.first {
            return String(firstInitial)
        }
        return ""
    }

    var profileName: String {
        if let firstName = activeAccount?.firstName, !firstName.isEmpty {
            return firstName
        }
        return activeAccount?.firstName ?? ""
    }

    var profileEmail: String {
        activeAccount?.email ?? ""
    }

    // Derived setting values
    var biologicalSexText: String {
        guard let sex = activeAccount?.gender else { return "" }
        return sex.rawValue.capitalized
    }

    var activityLevelText: String {
        activeAccount?.weightSettings?.activityLevel?.rawValue.capitalized ?? ""
    }

    var heightText: String {
        // Height is stored as tenths-of-inches (e.g. "681" == 5′8″ / 173 cm)
        guard let heightStr = activeAccount?.weightSettings?.height,
              let storedHeightDouble = Double(heightStr) else {
            return ""
        }

        let storedHeight = Int(round(storedHeightDouble))

        switch activeAccount?.weightSettings?.weightUnit {
        case .kg: // Metric preference – show centimeters
            let cm = ConversionTools.convertStoredHeightToCm(storedHeight)
            return "\(cm) cm"
        case .lb: // Imperial preference – show feet & inches
            let feet = ConversionTools.convertStoredHeightToFeet(storedHeight)
            return "\(feet[0])′ \(feet[1])″"  // → 5′ 8″
        case .none:
            return ""
        }
    }

    var unitTypeText: String {
        switch activeAccount?.weightSettings?.weightUnit {
        case .kg: return commonLang.unitKgCm
        case .lb: return commonLang.unitLbsFeet
        case .none: return ""
        }
    }

    var weightlessText: String {
        (activeAccount?.weightlessSettings?.isWeightlessOn ?? false) ? commonLang.on : commonLang.off
    }

    var notificationsOnText: String {
        guard let settings = activeAccount?.notificationSettings else {
            return commonLang.off
        }
        if settings.shouldSendEntryNotifications {
            return settings.shouldSendWeightInEntryNotifications ? "w/ Weight" : commonLang.on
        } else {
            return commonLang.off
        }
    }

    var streaksOnText: String { (activeAccount?.streaksSettings?.isStreakOn ?? false) ? commonLang.on : commonLang.off }
    
    var appearanceModeText: String {
        switch theme.appearanceMode {
        case .light:
            return commonLang.light
        case .dark:
            return commonLang.dark
        case .system:
            return commonLang.system
        }
    }
}
