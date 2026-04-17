import Foundation
@testable import meApp

enum GoalAlertTestFixtures {
    static func makeAccount(
        id: String = "goal-account-101",
        email: String = "goal@example.com",
        goalType: GoalType? = GoalType.none,
        goalWeight: Double? = nil,
        initialWeight: Double? = nil
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: email,
            isLoggedIn: true,
            isActiveAccount: true,
            goalType: goalType,
            goalWeight: goalWeight,
            initialWeight: initialWeight
        )
    }

    static func goalMetFlagKey(accountId: String) -> String {
        "\(accountId)-goalMetFlag"
    }
}
