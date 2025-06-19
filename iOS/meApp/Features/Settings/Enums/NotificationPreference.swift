//
//  NotificationPreference.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//


// MARK: - Notification Helpers
enum NotificationPreference: CaseIterable {
    case enable
    case enableWithWeight
    case disable
    
    var title: String {
        switch self {
        case .enable: return "Enable notifications"
        case .enableWithWeight: return "Enable notifications & include weight"
        case .disable: return "Disable Notifications"
        }
    }
}
