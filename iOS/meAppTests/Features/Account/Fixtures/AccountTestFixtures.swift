import Foundation
@testable import meApp

enum AccountTestError: Error, Equatable {
    case apiFailed
    case persistenceFailed
}

enum AccountTestFixtures {
    static func makeProfile(email: String = "user@example.com", firstName: String = "New", lastName: String = "User") -> Profile {
        Profile(
            firstName: firstName,
            lastName: lastName,
            email: email,
            gender: .male,
            zipcode: "10001",
            dob: "2000-01-01",
            weightUnit: .kg,
            height: 170,
            activityLevel: .normal
        )
    }

    static func makeBodyComp(weightUnit: WeightUnit = .kg, height: Double = 170, activityLevel: ActivityLevel = .normal) -> BodyComp {
        BodyComp(weightUnit: weightUnit, height: height, activityLevel: activityLevel)
    }

    static func makeNotifications(entry: Bool = true, weighIn: Bool = false) -> Notifications {
        Notifications(shouldSendEntryNotifications: entry, shouldSendWeightInEntryNotifications: weighIn)
    }

    static func makeGoal(type: GoalType = .lose, goalWeight: Int = 150, initialWeight: Int = 180) -> Goal {
        Goal(type: type, goalWeight: goalWeight, initialWeight: initialWeight, goalType: type)
    }

    static func makeTokens(access: String = "access-token", refresh: String = "refresh-token", expiresAt: String = "2099-01-01T00:00:00Z") -> Tokens {
        Tokens(accessToken: access, refreshToken: refresh, expiresAt: expiresAt)
    }

    static func makeAccountResponse(
        accountId: String = "100",
        email: String = "user@example.com",
        firstName: String = "First"
    ) -> AccountResponse {
        AccountResponse(
            account: makeAccountDTO(id: accountId, email: email, firstName: firstName),
            // swiftlint:disable:next no_hardcoded_credentials
            accessToken: "access-token",
            refreshToken: "refresh-token",
            expiresAt: "2099-01-01T00:00:00Z"
        )
    }

    static func makeGoalResponse(type: GoalType = .lose, goalWeight: Double = 150, initialWeight: Double = 180) -> GoalResponse {
        GoalResponse(type: type, goalWeight: goalWeight, createdAt: "2026-01-01T00:00:00Z", initialWeight: initialWeight)
    }

    static func makeAccountDTO(
        id: String = "100",
        email: String = "user@example.com",
        firstName: String = "First",
        productTypes: [String]? = nil,
        measurementUnits: String? = nil
    ) -> AccountDTO {
        AccountDTO(
            id: id,
            email: email,
            firstName: firstName,
            lastName: "Last",
            gender: .male,
            zipcode: "10001",
            weightUnit: .kg,
            isWeightlessOn: false,
            height: 170,
            activityLevel: .normal,
            dob: "2000-01-01",
            weightlessTimestamp: nil,
            weightlessWeight: nil,
            isStreakOn: false,
            streakTimestamp: nil,
            dashboardType: .dashboard4,
            dashboardMetrics: [.weight],
            progressMetrics: ["goal"],
            goalType: .maintain,
            goalWeight: nil,
            goalPercent: nil,
            initialWeight: nil,
            shouldSendEntryNotifications: true,
            shouldSendWeightInEntryNotifications: false,
            isFitbitOn: false,
            isFitbitValid: false,
            isMFPOn: false,
            isMFPValid: false,
            isHealthKitOn: false,
            isHealthConnectOn: false,
            productTypes: productTypes,
            measurementUnits: measurementUnits
        )
    }

    static func makeAccountModel(
        id: String = "100",
        email: String = "user@example.com",
        firstName: String = "First",
        isLoggedIn: Bool = true,
        isActive: Bool = false,
        isSynced: Bool = true
    ) -> Account {
        let account = Account(from: makeAccountDTO(id: id, email: email, firstName: firstName))
        account.isLoggedIn = isLoggedIn
        account.isActiveAccount = isActive
        account.isSynced = isSynced
        account.expiresAt = "2099-01-01T00:00:00Z"
        return account
    }

    // MARK: - AccountSnapshot factory

    static func makeAccountSnapshot(
        id: String = "100",
        email: String = "user@example.com",
        firstName: String? = "First",
        lastName: String? = "Last",
        gender: Sex? = .male,
        height: String? = "170",
        dob: String? = "2000-01-01",
        zipcode: String? = "10001",
        isLoggedIn: Bool = true,
        isExpired: Bool = false,
        isActiveAccount: Bool = false,
        fcmToken: String? = nil,
        lastActiveTime: String? = nil,
        isSynced: Bool = true,
        productTypes: [String] = [],
        measurementUnits: String? = nil,
        weightUnit: WeightUnit = .kg,
        weightHeight: String = "170",
        activityLevel: ActivityLevel? = .normal,
        goalType: GoalType? = .maintain,
        goalWeight: Double? = nil,
        initialWeight: Double? = nil,
        goalPercent: Double? = nil,
        goalIsSynced: Bool = false,
        isStreakOn: Bool = false,
        streakTimestamp: String? = nil,
        isWeightlessOn: Bool = false,
        weightlessWeight: Double? = nil,
        weightlessTimestamp: String? = nil,
        shouldSendEntryNotifications: Bool = true,
        shouldSendWeightInEntryNotifications: Bool = false,
        dashboardType: String? = nil,
        dashboardMetrics: String? = nil,
        progressMetrics: String? = nil,
        isHealthKitOn: Bool = false,
        isFitbitOn: Bool = false,
        isFitbitValid: Bool = false,
        isHealthConnectOn: Bool = false,
        isMfpOn: Bool = false,
        isMfpValid: Bool = false,
        accessToken: String? = nil,
        refreshToken: String? = nil,
        expiresAt: String? = "2099-01-01T00:00:00Z"
    ) -> AccountSnapshot {
        AccountSnapshot(
            accountId: id,
            email: email,
            firstName: firstName,
            lastName: lastName,
            gender: gender,
            height: height,
            dob: dob,
            zipcode: zipcode,
            isLoggedIn: isLoggedIn,
            isExpired: isExpired,
            isActiveAccount: isActiveAccount,
            fcmToken: fcmToken,
            lastActiveTime: lastActiveTime,
            isSynced: isSynced,
            productTypes: productTypes,
            measurementUnits: measurementUnits,
            weightUnit: weightUnit,
            weightHeight: weightHeight,
            activityLevel: activityLevel,
            goalType: goalType,
            goalWeight: goalWeight,
            initialWeight: initialWeight,
            goalPercent: goalPercent,
            goalIsSynced: goalIsSynced,
            isStreakOn: isStreakOn,
            streakTimestamp: streakTimestamp,
            isWeightlessOn: isWeightlessOn,
            weightlessWeight: weightlessWeight,
            weightlessTimestamp: weightlessTimestamp,
            shouldSendEntryNotifications: shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: shouldSendWeightInEntryNotifications,
            dashboardType: dashboardType,
            dashboardMetrics: dashboardMetrics,
            progressMetrics: progressMetrics,
            isHealthKitOn: isHealthKitOn,
            isFitbitOn: isFitbitOn,
            isFitbitValid: isFitbitValid,
            isHealthConnectOn: isHealthConnectOn,
            isMfpOn: isMfpOn,
            isMfpValid: isMfpValid,
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt
        )
    }

    // MARK: - Account flag fixtures

    static func makeAccountFlagDTO(
        id: String = "flag-1",
        type: String = "app-rate-ask",
        trigger: String = "login",
        metadata: [String: String]? = nil,
        createdAt: String = "2026-03-01T08:00:00Z",
        accountId: String = "100"
    ) -> AccountFlagDTO {
        AccountFlagDTO(
            id: id,
            type: type,
            trigger: trigger,
            metadata: metadata,
            createdAt: createdAt,
            accountId: accountId
        )
    }

    static func makeAccountFlagDTOs(ids: [String] = ["flag-1"], trigger: String = "login") -> [AccountFlagDTO] {
        ids.map { makeAccountFlagDTO(id: $0, trigger: trigger) }
    }
}
