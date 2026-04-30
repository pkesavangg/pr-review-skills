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
        case .enable: return "Enable Notifications without Weight"
        case .enableWithWeight: return "Enable Notifications w/ Weight"
        case .disable: return "Disable Notifications"
        }
    }
}
