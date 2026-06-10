import Foundation

extension Account {
    /// Converts the SwiftData Account and all its child models into a flat,
    /// Sendable AccountSnapshot. Call this only on the main actor while the
    /// model context is valid and before any await boundary.
    func toSnapshot(accessToken: String? = nil,
                    refreshToken: String? = nil,
                    expiresAt: String? = nil) -> AccountSnapshot {
        AccountSnapshot(
            // Core
            accountId: accountId,
            email: email,
            firstName: firstName,
            lastName: lastName,
            gender: gender,
            height: height,
            dob: dob,
            zipcode: zipcode,
            isLoggedIn: isLoggedIn ?? false,
            isExpired: isExpired ?? false,
            isActiveAccount: isActiveAccount ?? false,
            fcmToken: fcmToken,
            lastActiveTime: lastActiveTime,
            isSynced: isSynced ?? true,
            productTypes: productTypes,
            measurementUnits: measurementUnits,

            // WeightCompSettings
            weightUnit: weightSettings?.weightUnit ?? .lb,
            weightHeight: weightSettings?.height ?? "0",
            activityLevel: weightSettings?.activityLevel,

            // GoalSettings
            goalType: goalSettings?.goalType,
            goalWeight: goalSettings?.goalWeight,
            initialWeight: goalSettings?.initialWeight,
            goalPercent: goalSettings?.goalPercent,
            goalIsSynced: goalSettings?.isSynced ?? false,

            // StreaksSettings
            isStreakOn: streaksSettings?.isStreakOn ?? false,
            streakTimestamp: streaksSettings?.streakTimestamp,

            // WeightlessSettings
            isWeightlessOn: weightlessSettings?.isWeightlessOn ?? false,
            weightlessWeight: weightlessSettings?.weightlessWeight,
            weightlessTimestamp: weightlessSettings?.weightlessTimestamp,

            // NotificationSettings
            shouldSendEntryNotifications:
                notificationSettings?.shouldSendEntryNotifications ?? true,
            shouldSendWeightInEntryNotifications:
                notificationSettings?.shouldSendWeightInEntryNotifications ?? false,

            // DashboardSettings
            dashboardType: dashboardSettings?.dashboardType,
            dashboardMetrics: dashboardSettings?.dashboardMetrics,
            progressMetrics: dashboardSettings?.progressMetrics,

            // IntegrationSettings
            isHealthKitOn: integrationSettings?.isHealthKitOn ?? false,
            isFitbitOn: integrationSettings?.isFitbitOn ?? false,
            isFitbitValid: integrationSettings?.isFitbitValid ?? false,
            isHealthConnectOn: integrationSettings?.isHealthConnectOn ?? false,
            isMfpOn: integrationSettings?.isMfpOn ?? false,
            isMfpValid: integrationSettings?.isMfpValid ?? false,

            // Tokens (in-memory, caller passes these after Keychain hydration)
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt
        )
    }
}
