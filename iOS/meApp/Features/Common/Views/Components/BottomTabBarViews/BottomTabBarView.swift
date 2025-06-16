//
//  BottomTabBarView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//
import SwiftUI

// MARK: - BottomTabBarView
/// A view that manages the app's bottom tab navigation interface.
///
/// `BottomTabBarView` is the root container for rendering the currently selected tab content
/// and displaying a bottom tab bar for switching between tabs.
///
/// Features:
/// - Renders the currently active tab's view using a `ZStack` with `opacity` switching.
/// - Displays a custom tab bar at the bottom using a horizontal `HStack`.
/// - Supports dynamic visibility of tabs (e.g. `AppSync` tab shown conditionally).
/// - Displays a settings badge when there are unread feed items.
/// - Controls tab bar visibility (e.g. hides tab bar on detail pages).
///
/// This view should be used as the main entry point for tab-based navigation in the app.
/// The tabs are defined via the `BottomTab` enum, and the state is managed by `BottomTabBarViewModel`.
struct BottomTabBarView: View {
    @StateObject private var viewModel = BottomTabBarViewModel()
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                ForEach(viewModel.visibleTabs, id: \.self) { tab in
                    tab.view
                        .opacity(viewModel.selectedTab == tab ? 1 : 0)
                        .allowsHitTesting(viewModel.selectedTab == tab)
                }
            }
            
            if viewModel.showTabBar {
                HStack {
                    ForEach(Array(viewModel.visibleTabs.enumerated()), id: \.element) { index, tab in
                        Spacer()
                        Button {
                            viewModel.selectTab(tab)
                        } label: {
                            TabBarItemView(
                                tab: tab,
                                isSelected: viewModel.selectedTab == tab,
                                showSettingsBadge: viewModel.showSettingsBadge
                            )
                        }
                        .padding(.leading, index == 0 ? .spacingSM : 0)
                        .padding(.trailing, index == viewModel.visibleTabs.count - 1 ? .spacingSM : 0)
                        Spacer()
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, .spacingMD)
                .background(theme.backgroundPrimary)
                .border(sides: [.top], thickness: 0.5)
            }
        }
        .environmentObject(viewModel)
        .edgesIgnoringSafeArea(.bottom)
    }
}


#Preview {
    BottomTabBarView()
        .environmentObject(Theme.shared)
}
