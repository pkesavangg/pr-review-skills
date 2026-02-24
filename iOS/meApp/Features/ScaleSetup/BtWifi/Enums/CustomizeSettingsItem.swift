//
//  CustomizeSettingsItem.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/07/25.
//

/// Settings items for the customize settings screen
enum CustomizeSettingsItem: String, CaseIterable {
    case dashboardMetrics
    case scaleMetrics
    case scaleModes
    case userName
    
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
    
    /// Maps the settings item to the corresponding CustomizeSettings enum
    var customizeSettingsType: CustomizeSettings {
        switch self {
        case .dashboardMetrics:
            return .dashboardMetrics
        case .scaleMetrics:
            return .scaleMetrics
        case .scaleModes:
            return .scaleMode
        case .userName:
            return .scaleUsername
        }
    }
}
