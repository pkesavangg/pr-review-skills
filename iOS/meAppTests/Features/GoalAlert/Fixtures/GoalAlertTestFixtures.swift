import Foundation
@testable import meApp

enum GoalAlertTestFixtures {
    static func makeAccount(
        id: String = "goal-account-101",
        email: String = "goal@example.com",
        goalType: GoalType? = GoalType.none,
        goalWeight: Double? = nil,
        initialWeight: Double? = nil
    ) -> Account {
        let account = AccountTestFixtures.makeAccountModel(
            id: id,
            email: email,
            isLoggedIn: true,
            isActive: true
        )
        account.goalSettings?.goalType = goalType
        account.goalSettings?.goalWeight = goalWeight
        account.goalSettings?.initialWeight = initialWeight
        return account
    }

    static func goalMetFlagKey(accountId: String) -> String {
        "\(accountId)-goalMetFlag"
    }
}
