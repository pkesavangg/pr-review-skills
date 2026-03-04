import Foundation
@testable import meApp

enum AuthTestFixtures {
    static func makeAccount(id: String = "101", email: String = "user@example.com", firstName: String = "Test") -> Account {
        let dto = AccountDTO(
            id: id,
            email: email,
            firstName: firstName,
            lastName: "User",
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
            isHealthConnectOn: false
        )
        let account = Account(from: dto)
        account.isLoggedIn = true
        account.isActiveAccount = true
        account.expiresAt = "2099-01-01T00:00:00Z"
        return account
    }
}
