//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import Foundation
import SwiftUI
import Combine

@MainActor
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedService
    @Published var selectedTab: BottomTab = .dash
    @Published var showSettingsBadge: Bool = false
    @Published var showAppSync: Bool = false
    @Published var showTabBar: Bool = true
    /// Holds the body-composition metrics captured by AppSync when the user taps **Edit** on the confirmation card.
    /// `ManualEntryScreen` will consume this to pre-populate its form, then reset it to `nil`.
    @Published var pendingAppSyncEditMetrics: AppSyncEntryMetrics? = nil
    /// Holds a pending navigation request to be performed by `SettingsScreen` once it appears.
    /// This is set when the user taps *Connect* in the *Add Apple Health Integration* modal.
    @Published var pendingSettingsNavigation: SettingsRoute? = nil
    
    // MARK: - Dependencies
    @Injector private var healthKitService: HealthKitService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    
    private let tag = "BottomTabBarViewModel"
    private var cancellables: Set<AnyCancellable> = []
    
    init() {
        self.showSettingsBadge = feedService.getUnreadFeedCount() > 0
        // TODO: Update the app sync display based on the app sync scale defined in the paired scale list
        
        // Perform Apple Health integration check on launch
        Task { [weak self] in
            await self?.checkAppleHealthIntegrationStatus()
        }
        
        // Update the app sync tab based on the app sync scale defined in the paired scale list
        scaleService.$scales
            .map { scales in
                scales.contains { $0.bathScale?.scaleType == ScaleSourceType.appsync.rawValue }
            }
            .receive(on: DispatchQueue.main)
            .assign(to: \.showAppSync, on: self)
            .store(in: &cancellables)
    }
    
    // MARK: - Tab Deactivation Handling
    /// A dictionary holding async deactivation handlers for each tab. A handler should return `true` if it is safe
    /// to leave the current tab, or `false` to cancel navigation. Views are responsible for registering and removing
    /// their own handlers via `registerDeactivationHandler` / `removeDeactivationHandler`.
    private var deactivationHandlers: [BottomTab: () async -> Bool] = [:]
    
    /// Registers a de-activation handler for the given tab, overriding any existing handler.
    /// - Parameters:
    ///   - tab:     The `BottomTab` for which the handler applies.
    ///   - handler: An async closure returning a `Bool` indicating whether the tab can be left.
    func registerDeactivationHandler(for tab: BottomTab, handler: @escaping () async -> Bool) {
        deactivationHandlers[tab] = handler
    }
    
    /// Removes any previously registered de-activation handler for the given tab.
    /// - Parameter tab: The `BottomTab` whose handler should be removed.
    func removeDeactivationHandler(for tab: BottomTab) {
        deactivationHandlers.removeValue(forKey: tab)
    }
    
    /// Returns the currently registered de-activation handler for a tab, if available.
    /// - Parameter tab: The tab whose handler is requested.
    func deactivationHandler(for tab: BottomTab) -> (() async -> Bool)? {
        deactivationHandlers[tab]
    }
    
    var visibleTabs: [BottomTab] {
        var tabs: [BottomTab] = [.dash, .entry, .history, .settings]
        if showAppSync {
            tabs.append(.appsync)
        }
        return tabs
    }
    
    func selectTab(_ tab: BottomTab) {
        selectedTab = tab
    }
    
    /// Navigates to the goal setting screen via the settings tab
    func navigateToGoalSetting() {
        selectTab(.settings)
        pendingSettingsNavigation = .goal
    }

    // MARK: - Apple Health Integration Prompt
    /// Checks whether the user should be prompted to add Apple Health integration
    /// and shows the modal if needed.
    private func checkAppleHealthIntegrationStatus() async {
        do {
            if let modalState = try await healthKitService.shouldShowHKIntegrationModal() {
                await MainActor.run { [weak self] in
                    switch modalState {
                    case .addIntegration, .finishAdding:
                        self?.presentHKIntegrationModal(for: modalState)
                    case .outOfSync:
                        self?.presentHKIntegrationModal(for: .outOfSync)
                    }
                }
            }
        } catch {
            // Silently ignore – logging is handled in `HealthKitService`
        }
    }
    
    /// Presents the Apple Health Integration modal based on the given state.
    private func presentHKIntegrationModal(for state: HKIntegrationModalState) {
        // Configure actions based on modal state
        let onPrimary: () -> Void
        let onSecondary: (() -> Void)?
        
        switch state {
        case .addIntegration, .finishAdding:
            onPrimary = { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                // Switch to Settings tab and queue navigation to the Integrations screen.
                self.selectTab(.settings)
                self.pendingSettingsNavigation = .integrations
            }
            onSecondary = nil
            
        case .outOfSync:
            onPrimary = { [weak self] in
                guard let self else { return }
                self.healthKitService.openAppleHealth()
                self.notificationService.dismissModal()
            }
            
            onSecondary = { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                Task {
                    self.notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
                    do {
                        try await self.healthKitService.clearHealthKit()
                        self.notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationRemoved))
                    } catch {
                        self.logger.log(level: .error, tag: self.tag, message: "Failed to clear HealthKit data", data: error.localizedDescription)
                    }
                    self.notificationService.dismissLoader()
                }
            }
        }
        
        let modalView = HKIntegrationModalView(
            state: state,
            onClose: { [weak notificationService] in
                notificationService?.dismissModal()
            },
            onPrimaryTap: onPrimary,
            onSecondaryTap: onSecondary
        )
        
        let modalData = ModalData(presentedView: AnyView(modalView))
        notificationService.showModal(modalData)
    }
}
