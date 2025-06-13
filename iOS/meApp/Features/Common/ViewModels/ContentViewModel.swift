// LoadingScreenViewModel.swift
// meApp
//
// Created by Lakshmi Priya on 13/06/25.

import Foundation
import Combine

@MainActor
final class ContentViewModel: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var currentAccount: Account?
    @Published var showDashboardText: Bool = false
    @Published var showLandingText: Bool = false
    @Published var isInitializing: Bool = true
    @Published var entries: [Entry] = []

    @Injector var accountService: AccountService
    @Injector var scaleService : ScaleService
    @Injector var feedService : FeedService
    @Injector var entryService : EntryService
    @Injector var logger : LoggerService
    @Injector var pushNotificationService : PushNotificationService

    func performAppInitialization() async {
        isInitializing = true
        let loggedIn = await checkLoginStatus()
        if loggedIn {
            // Check if notifications are required for the current account/scales
            let notificationsRequired = await areNotificationsRequired()
            if notificationsRequired {
                await pushNotificationService.setupPushNotifications()
            } else {
                await pushNotificationService.updateDeviceInfo()
            }
            await loadData()
        }
        await routeToLandingOrApp(isLoggedIn: loggedIn)
        isInitializing = false
    }

    // MARK: - Login Status Check
    private func checkLoginStatus() async -> Bool {
        do {
            try await accountService.updatePublishedState()
            currentAccount = accountService.activeAccount
        } catch {
            currentAccount = nil
        }
        isLoggedIn = (currentAccount != nil)
        return isLoggedIn
    }

    // MARK: - Data Loading (if logged in)
    private func loadData() async {
        try? await Task.sleep(nanoseconds: 5_000_000_000)
        await scaleService.syncAllScalesWithRemote()
        guard let _ = currentAccount else { return }
        await entryService.syncAllEntriesWithRemote()

        do {
            entries = try await entryService.getAllEntries()
        } catch {
            entries = []
        }
        do {
            try await feedService.fetchFeedItems()
        } catch {
            logger.log(level: .error, tag: "ContentViewModel", message: "Failed to fetch feed items: \(error)")
        }
    }

    // MARK: - Routing Logic
    func routeToLandingOrApp(isLoggedIn: Bool) async {
        showDashboardText = isLoggedIn
        showLandingText = !isLoggedIn
    }

    // MARK: - View Texts - For Testing Purposes.
    func dashboardTextView() -> String { "Dashboard Screen" }
    func landingTextView() -> String { "Landing Screen" }

    // MARK: - Notification Permission Logic
    private func areNotificationsRequired() async -> Bool {
        // TODO: Replace with real logic to check if notifications are required for paired scales or user settings
        // For now, always return true to mimic wgApp4 logic
        return true
    }

    // MARK: - Force Login/Logout for Testing
    @MainActor
    func forceLogin() async {
        do {
            _ = try await accountService.logIn(email: "lakshmipriya@greatergoods.com", password: "123456")
            await performAppInitialization()
        } catch {
            logger.log(level: .error, tag: "ContentViewModel", message: "Force login failed: \(error)")
        }
    }

    @MainActor
    func forceLogout() async {
        do {
            try await accountService.logOut(accountId: accountService.activeAccount?.accountId)
            await performAppInitialization()
        } catch {
            logger.log(level: .error, tag: "ContentViewModel", message: "Force logout failed: \(error)")
        }
    }
}
