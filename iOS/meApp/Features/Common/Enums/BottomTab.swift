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
        case .entry: EntryView()
        case .history: HistoryView()
        case .settings: SettingsView()
        case .appsync: AppSyncView()
        }
    }
}

// TODO: Test Views need to replace with actual views
struct DashboardView: View {
    @EnvironmentObject private var viewModel: BottomTabBarViewModel
    
    var body: some View {
        NavigationStack {
            List(1..<100) { item in
                NavigationLink {
                    DetailScreen(item: item)
                        .onAppear { viewModel.showTabBar = false }
                } label: {
                    Text("Item \(item)")
                        .font(.headline)
                        .padding()
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(8)
                }
            }
            .onAppear { viewModel.showTabBar = true }
        }
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



struct EntryView: View {
    var body: some View {
        NavigationStack {
            List(1..<100) { item in
                NavigationLink {
                    Text("Entry View Detail for Item \(item)")
                        .font(.largeTitle)
                        .navigationTitle("Item \(item)")
                } label: {
                    Text("Entry view item \(item)")
                        .font(.headline)
                        .padding()
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(8)
                }
            }
        }
    }
}

struct HistoryView: View {
    var body: some View {
        Text("History View")
            .font(.largeTitle)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
    }
}

struct SettingsView: View {
    var body: some View {
        Text("Settings View")
            .font(.largeTitle)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
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
