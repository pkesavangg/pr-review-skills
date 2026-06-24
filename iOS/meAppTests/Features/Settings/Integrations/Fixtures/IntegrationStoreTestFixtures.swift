import Foundation
@testable import meApp

enum IntegrationStoreTestFixtures {
    static func makeAccount(
        id: String = "101",
        fitbitOn: Bool = false,
        fitbitValid: Bool = true,
        mfpOn: Bool = false,
        mfpValid: Bool = true
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "user@example.com",
            isLoggedIn: true,
            isActiveAccount: true,
            isFitbitOn: fitbitOn,
            isFitbitValid: fitbitValid,
            isMfpOn: mfpOn,
            isMfpValid: mfpValid
        )
    }
}
