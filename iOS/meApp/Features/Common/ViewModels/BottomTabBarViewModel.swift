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
