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
    /// A dictionary to hold deactivation handlers for each tab.
    @State private var deactivationHandlers: [BottomTab: () async -> Bool] = [:]
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                ForEach(viewModel.visibleTabs, id: \.self) { tab in
                    tab.view
                        .environment(\.registerTabDeactivationHandler) { handler in
                            // Register a deactivation handler for the tab
                            deactivationHandlers[tab] = handler
                        }
                        .opacity(viewModel.selectedTab == tab ? 1 : 0)
                        .allowsHitTesting(viewModel.selectedTab == tab)
                        .onDisappear { viewModel.removeDeactivationHandler(for: tab) }
                }
            }
            
            if viewModel.showTabBar {
                HStack {
                    ForEach(Array(viewModel.visibleTabs.enumerated()), id: \.element) { index, tab in
                        Spacer()
                        Button {
                            handleTabSelection(tab)
                        } label: {
                            TabBarItemView(
                                tab: tab,
                                isSelected: viewModel.selectedTab == tab,
                                showSettingsBadge: viewModel.canShowFeedNotificationBadge
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
        .withWeightOnlyModeIndicator()
        .environmentObject(viewModel)
        .edgesIgnoringSafeArea(.bottom)
        // Half-sheet shown when a new scale is discovered via Bluetooth
        .sheet(item: $viewModel.discoveredScale) { scale in
            ScaleDiscoveredSheetView(
                device: scale,
                discoveryEvent: viewModel.discoveryEvent,
                onClose: {
                    viewModel.dismissDiscoveredScaleSheet()
                },
                onConnect: {
                    viewModel.openScaleSetup(scale: scale, event: viewModel.discoveryEvent)
                }
            )
            .deviceDiscoverSheetStyle()
        }
        // Setup flow presentation
        .sheet(item: $viewModel.setupPayload, onDismiss: {
            viewModel.bluetoothService.isSetupInProgress = false
        }) { payload in
            // Determine setup type from the scale item info
            let setupType = payload.event?.deviceInfo.setupType ?? .lcbt
            switch setupType {
            case .lcbt:
                A6ScaleSetupScreen(sku: payload.sku,
                                   discoveredScale: payload.scale,
                                   discoveryEvent: payload.event)
                .interactiveDismissDisabled(true)
            case .btWifiR4:
                BtWifiScaleSetupScreen(sku: payload.sku,
                                       discoveredScale: payload.scale,
                                       discoveryEvent: payload.event,
                                       isReconnect: payload.isReconnect,
                                       isDuplicated: payload.isDuplicated)
                .interactiveDismissDisabled(true)
            default:
                // Fallback to A6 setup for other types
                A6ScaleSetupScreen(sku: payload.sku,
                                   discoveredScale: payload.scale,
                                   discoveryEvent: payload.event)
                .interactiveDismissDisabled(true)
            }
        }
    }
    
    // MARK: - Helpers
    private func handleTabSelection(_ tab: BottomTab) {
        guard viewModel.selectedTab != tab else { return }
        
        Task {
            // Check camera permission for AppSync tab
            if tab == .appsync {
                let permissionState = await viewModel.handleCameraPermission()
                guard permissionState == .ENABLED else { return }
            }
            
            // Check if there is a deactivation handler for the selected tab
            // If there is, call it and await the result
            // If it returns true, switch to the new tab
            if let canDeactivate = deactivationHandlers[viewModel.selectedTab] {
                let allowSwitch = await canDeactivate()
                if allowSwitch {
                    withAnimation {
                        viewModel.selectTab(tab)
                    }
                }
            } else {
                withAnimation {
                    viewModel.selectTab(tab)
                }
            }
        }
    }
}


#Preview {
    BottomTabBarView()
        .environmentObject(Theme.shared)
}
