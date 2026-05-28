//
//  SettingsStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Foundation

struct SettingsStrings {
    static let title = "Settings"

    // Account Settings
    static let accountSettings = "Account"
    static let addEditScales = "My Devices"
    static let myKids = "My Kids"
    static let integrations = "Integrations"
    static let exportData = "Export Data"
    static let changePassword = "Change Password"
    static let userProfile = "User Profile"

    // Weight Scale Settings
    static let weightScaleSettings = "Weight Scale"

    // Profile Settings
    static let profileSettings = "Profile Settings"
    static let goalSetting = "Goal Setting"
    static let biologicalSex = "Biological Sex"
    static let activityLevel = "Activity Level"
    static let height = "Height"
    static let unitType = "Unit Type"
    static let weightless = "Weightless"

    // App Settings
    static let appSettings = "App"
    static let notifications = "Notifications"
    static let messages = "Messages"
    static func messagesWithNew(_ count: Int) -> String { count > 0 ? "Messages (\(count) new)" : messages }
    static let streaks = "Streaks"
    static let appPermissions = "App Permissions"
    static let appearance = "Appearance"
    static let defaultGraphView = "Default Graph View"

    // Support
    static let supportSettings = "Support"
    static let helpAndCustomerService = "Help & Customer Service"
    static let privacyPolicy = "Privacy Policy"
    static let termsOfService = "Terms of Service"
    static let greaterGoodsWebsite = "GreaterGoods.com"

    // Account Actions
    static let logOut = "Log Out"
    static let logOutAllAccount = "Log Out of All Accounts"
    static let switchAccounts = "Switch Accounts"
    static let deleteAccount = "Delete Account"
}
