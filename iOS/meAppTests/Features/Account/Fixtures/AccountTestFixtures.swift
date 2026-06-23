//
//  AccountTestFixtures.swift
//  meAppTests
//

import Foundation
@testable import meApp

/// Reusable factory methods for building test objects in account-related tests.
@MainActor
enum AccountTestFixtures {

    // MARK: - AccountDTO

    static func makeAccountDTO(
        id: String = "test-account-id",
        email: String = "test@example.com",
        firstName: String = "Test",
        lastName: String? = "User",
        gender: Sex = .male,
        zipcode: String? = "12345",
        weightUnit: WeightUnit = .lb,
        height: Double = 700,
        dob: String = "1990-01-01",
        goalType: GoalType? = nil,
        goalWeight: Double? = nil
    ) -> AccountDTO {
        AccountDTO(
            id: id,
            email: email,
            firstName: firstName,
            lastName: lastName,
            gender: gender,
            zipcode: zipcode,
            weightUnit: weightUnit,
            isWeightlessOn: nil,
            height: height,
            activityLevel: .normal,
            dob: dob,
            weightlessTimestamp: nil,
            weightlessWeight: nil,
            isStreakOn: nil,
            streakTimestamp: nil,
            dashboardType: nil,
            dashboardMetrics: nil,
            progressMetrics: nil,
            goalType: goalType,
            goalWeight: goalWeight,
            goalPercent: nil,
            initialWeight: nil,
            shouldSendEntryNotifications: nil,
            shouldSendWeightInEntryNotifications: nil,
            isFitbitOn: nil,
            isFitbitValid: nil,
            isMFPOn: nil,
            isMFPValid: nil,
            isHealthKitOn: nil,
            isHealthConnectOn: nil
        )
    }

    // MARK: - AccountResponse

    static func makeAccountResponse(
        id: String = "test-account-id",
        email: String = "test@example.com",
        accessToken: String = "access-token-123",
        refreshToken: String = "refresh-token-456",
        expiresAt: String = "2099-01-01T00:00:00Z"
    ) -> AccountResponse {
        AccountResponse(
            account: makeAccountDTO(id: id, email: email),
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt
        )
    }

    // MARK: - Profile

    static func makeProfile(
        firstName: String = "Test",
        lastName: String = "User",
        email: String? = "test@example.com",
        gender: Sex = .male,
        zipcode: String = "12345",
        dob: String = "1990-01-01",
        weightUnit: WeightUnit = .lb,
        height: Double = 700,
        activityLevel: ActivityLevel = .normal
    ) -> Profile {
        Profile(
            firstName: firstName,
            lastName: lastName,
            email: email,
            gender: gender,
            zipcode: zipcode,
            dob: dob,
            weightUnit: weightUnit,
            height: height,
            activityLevel: activityLevel
        )
    }

    // MARK: - Goal

    static func makeGoal(
        type: GoalType = .lose,
        goalWeight: Int = 1600,
        initialWeight: Int = 2000,
        goalType: GoalType = .lose
    ) -> Goal {
        Goal(
            type: type,
            goalWeight: goalWeight,
            initialWeight: initialWeight,
            goalType: goalType
        )
    }

    // MARK: - GoalResponse

    static func makeGoalResponse(
        type: GoalType = .lose,
        goalWeight: Double = 1600,
        initialWeight: Double = 2000,
        createdAt: String = "2026-01-01T00:00:00Z"
    ) -> GoalResponse {
        GoalResponse(
            type: type,
            goalWeight: goalWeight,
            createdAt: createdAt,
            initialWeight: initialWeight
        )
    }

    // MARK: - Account (@Model)

    static func makeAccount(
        id: String = "test-account-id",
        email: String = "test@example.com",
        isActiveAccount: Bool = true
    ) -> Account {
        let dto = makeAccountDTO(id: id, email: email)
        let account = Account(from: dto)
        account.isActiveAccount = isActiveAccount
        account.isLoggedIn = true
        account.isSynced = true
        return account
    }
}
