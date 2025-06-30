//
//  SettingsBannerType.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import Foundation

enum ScaleStatusBannerType {
    case setupIncomplete(onTap: () -> Void)
    case weightOnly(onTap: () -> Void)
    
    var iconName: String {
        switch self {
        case .setupIncomplete: return AppAssets.exclamationMark
        case .weightOnly: return AppAssets.weightOnlyMode
        }
    }
    
    var message: String {
        switch self {
        case .setupIncomplete: return ScaleSettingsStrings.setupIncomplete
        case .weightOnly: return ScaleSettingsStrings.weightOnlyOn
        }
    }
    
    var actionTitle: String {
        switch self {
        case .setupIncomplete: return ScaleSettingsStrings.setupWiFi
        case .weightOnly: return ScaleSettingsStrings.enableBodyMetrics
        }
    }
    
    var onTap: () -> Void {
        switch self {
        case .setupIncomplete(let action): return action
        case .weightOnly(let action): return action
        }
    }
}
