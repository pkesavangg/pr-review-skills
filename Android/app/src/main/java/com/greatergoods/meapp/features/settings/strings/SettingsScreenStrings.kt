package com.greatergoods.meapp.features.settings.strings

// TODO: MyAccountsScreenStrings will be implemented in a new file in this folder, following the PascalCase object pattern for static text.

object SettingsScreenStrings {
    const val Title = "Settings"

    // Account Settings
    const val AccountSettings = "Account Settings"
    const val AddEditScales = "Add & Edit Scales"
    const val Integrations = "Integrations"
    const val ExportData = "Export Data"
    const val ChangePassword = "Change Password"
    const val UserProfile = "User Profile"

    // Profile Settings
    const val ProfileSettings = "Profile Settings"
    const val GoalSetting = "Goal Setting"
    const val BiologicalSex = "Biological Sex"
    const val ActivityLevel = "Activity Level"
    const val Height = "Height"
    const val UnitType = "Unit Type"
    const val Weightless = "Weightless"

    // App Settings
    const val AppSettings = "App Settings"
    const val Notifications = "Notifications"
    const val Messages = "Messages"
    const val AppPermissions = "App Permissions"

    // Support
    const val Support = "Support"
    const val HelpCustomerService = "Help & Customer Service"
    const val PrivacyPolicy = "Privacy Policy"
    const val TermsOfService = "Terms of Service"
    const val GreaterGoodsDotCom = "GreaterGoods.com"

    // Actions
    const val SwitchAccounts = "Switch Accounts"
    const val LogOut = "Log Out"
    const val DeleteAccount = "Delete Account"
    const val Edit = "EDIT"

    const val ProfileImage = "Profile Image"
    const val LoggingOut = "Logging out..."

    object LogoutDialog {
        const val Title = "Log out"
        const val Body = "Are you sure you want to log out?"
        const val Confirm = "Log out"
        const val Cancel = "Cancel"
    }

    // URL Constants (extracted from TypeScript account-settings.page.ts)
    object Urls {
        const val PrivacyPolicy = "https://greatergoods.com/legal/privacy-policy"
        const val TermsOfService = "https://greatergoods.com/legal/weight-gurus-tos"
        const val GreaterGoodsWebsite = "https://greatergoods.com"
    }
}
