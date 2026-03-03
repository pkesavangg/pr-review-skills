import Foundation
@testable import meApp

enum IntegrationStoreTestFixtures {
    static func makeAccount(
        id: String = "101",
        fitbitOn: Bool = false,
        fitbitValid: Bool = true,
        mfpOn: Bool = false,
        mfpValid: Bool = true
    ) -> Account {
        let account = AccountTestFixtures.makeAccountModel(
            id: id,
            email: "user@example.com",
            isLoggedIn: true,
            isActive: true
        )

        account.integrationSettings?.isFitbitOn = fitbitOn
        account.integrationSettings?.isFitbitValid = fitbitValid
        account.integrationSettings?.isMfpOn = mfpOn
        account.integrationSettings?.isMfpValid = mfpValid
        return account
    }
}
