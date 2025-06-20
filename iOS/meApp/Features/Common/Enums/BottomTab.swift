//
//  BottomTab.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import SwiftUI
// MARK: - BottomTab
/// Represents the bottom tab bar items in the application.
/// Each tab has a label, icon, filled icon, and a corresponding view.
enum BottomTab: String, CaseIterable {
    case dash, entry, history, settings, appsync

    var label: String {
        switch self {
        case .dash: return CommonStrings.dash
        case .entry: return CommonStrings.entry
        case .history: return CommonStrings.history
        case .settings: return CommonStrings.settings
        case .appsync: return CommonStrings.appSync
        }
    }

    var icon: String {
        switch self {
        case .dash: return AppAssets.dash
        case .entry: return AppAssets.addEntry
        case .history: return AppAssets.history
        case .settings: return AppAssets.settings
        case .appsync: return AppAssets.appSync
        }
    }

    var filledIcon: String {
        switch self {
        case .dash: return AppAssets.dashFill
        case .entry: return AppAssets.addEntryFill
        case .history: return AppAssets.historyFill
        case .settings: return AppAssets.settingsFill
        case .appsync: return AppAssets.appSync
        }
    }

    @ViewBuilder
    var view: some View {
        switch self {
        case .dash: DashboardView()
        case .entry: ManualEntryScreen()
        case .history: HistoryListScreen()
        case .settings: SettingsScreen()
        case .appsync: AppSyncView()
        }
    }
}

// TODO: Test Views need to replace with actual views
struct DashboardView: View {
    @Environment(\.appTheme) private var theme
    var body: some View {
        
        WeightTrendView()
          
        Spacer()
    }
}

struct DetailScreen: View {
    let item: Int
    @EnvironmentObject private var viewModel: BottomTabBarViewModel
    var body: some View {
        NavigationLink {
            Text("In Detail for Item \(item)")
                .onAppear { viewModel.showTabBar = true }
        } label: {
            Text("Detail for Item \(item)")
                .font(.largeTitle)
                .navigationTitle("Item \(item)")
        }

    }
}

struct AppSyncView: View {
    var body: some View {
        Text("AppSync View")
            .font(.largeTitle)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
    }
}
