//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import Foundation

@MainActor
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedService
    @Published var selectedTab: BottomTab = .dash
    @Published var showSettingsBadge: Bool = false
    @Published var showAppSync: Bool = true
    @Published var showTabBar: Bool = true
    
    init() {
        self.showSettingsBadge = true // feedService.getUnreadFeedCount() > 0
        // TODO: Update the app sync display based on the app sync scale defined in the paired scale list
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
}
