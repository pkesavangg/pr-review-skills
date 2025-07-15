//
//  CustomizeSettingsView.swift
//  meApp
//
//  Created by Cursor AI on 12/07/25.
//

import SwiftUI

/// View for customizing scale settings during BtWifi setup flow.
struct CustomizeSettingsView: View {
    // MARK: - Environment
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var setupStore: BtWifiScaleSetupStore
    
    // MARK: - State
    @State private var selectedItems: Set<SettingsItem> = []
    
    // MARK: - Constants
    private let strings = BtWifiScaleSetupStrings.CustomizeSettingsStrings.self
    private let appAssets = AppAssets.self
    
    // MARK: - Settings Items
    enum SettingsItem: String, CaseIterable {
        case dashboardMetrics = "dashboardMetrics"
        case scaleMetrics = "scaleMetrics"
        case scaleModes = "scaleModes"
        case userName = "userName"
        
        var title: String {
            switch self {
            case .dashboardMetrics:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.dashboardMetricsTitle
            case .scaleMetrics:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.scaleMetricsTitle
            case .scaleModes:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.scaleModesTitle
            case .userName:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.userNameTitle
            }
        }
        
        var subtitle: String {
            switch self {
            case .dashboardMetrics:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.dashboardMetricsSubtitle
            case .scaleMetrics:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.scaleMetricsSubtitle
            case .scaleModes:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.scaleModesSubtitle
            case .userName:
                return BtWifiScaleSetupStrings.CustomizeSettingsStrings.userNameSubtitle
            }
        }
        
        var icon: String {
            switch self {
            case .dashboardMetrics:
                return AppAssets.grid
            case .scaleMetrics:
                return AppAssets.metric
            case .scaleModes:
                return AppAssets.weightOnlyMode
            case .userName:
                return AppAssets.scale
            }
        }
    }
    
    var body: some View {
            VStack(alignment: .leading) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(strings.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    
                    Text(strings.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.center)
                }
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        VStack(alignment: .leading, spacing: .spacingLG) {
                            // Settings Items
                            VStack(spacing: .spacingSM) {
                                ForEach(SettingsItem.allCases, id: \.rawValue) { item in
                                    settingsItemView(item: item)
                                }
                            }
                            Spacer()
                        }
                    }
                }
            }
            .padding(.top, .spacingLG)
            .background(theme.backgroundSecondary)
    }
    
    /// Creates a settings item view with icon, text, and checkmark.
    private func settingsItemView(item: SettingsItem) -> some View {
        Button {
            withAnimation {
                addSelection(for: item)
            }
        } label: {
            VStack(alignment: .leading) {
                HStack(spacing: .spacingSM) {
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        HStack(spacing: .spacingXS) {
                            AppIconView(icon: item.icon)
                                .foregroundColor(theme.actionPrimary)
                            Text(item.title)
                                .fontOpenSans(.heading5)
                                .foregroundColor(theme.textHeading)
                                .multilineTextAlignment(.leading)
                        }
                        Text(item.subtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    let icon = selectedItems.contains(item) ? AppAssets.filledTickCircle : AppAssets.chevronRight
                    AppIconView(icon:  icon)
                        .foregroundColor(theme.actionPrimary)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingMD)
            }
            .background(theme.backgroundPrimary)
            .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
        }
    }
    
    /// Toggles the selection state for a settings item.
    private func addSelection(for item: SettingsItem) {
        selectedItems.insert(item)
    }
}

#Preview {
    CustomizeSettingsView()
        .environmentObject(BtWifiScaleSetupStore())
        .environmentObject(Theme.shared)
}
