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

    func performAppInitialization() async {
        isInitializing = true
        let loggedIn = await checkLoginStatus()
        if loggedIn { await loadData() }
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
}
