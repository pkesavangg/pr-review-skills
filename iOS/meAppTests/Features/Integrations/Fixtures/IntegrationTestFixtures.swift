import Foundation
@testable import meApp

enum IntegrationTestFixtures {
    static func makeIntegrationInfo(
        type: IntegrationType = .healthKit,
        isIntegrated: Bool = true,
        assignedTo: String? = "account-101"
    ) -> IntegrationInfo {
        IntegrationInfo(
            type: type,
            isIntegrated: isIntegrated,
            assignedTo: assignedTo,
            deIntegrated: nil
        )
    }
}
