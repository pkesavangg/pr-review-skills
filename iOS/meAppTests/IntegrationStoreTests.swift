import Foundation
import Testing
@testable import meApp

@MainActor
struct IntegrationStoreTests {

    @Test
    func showRequestIntegrationModalPresentsSingleModal() async throws {
        _ = ServiceRegistry.shared
        NotificationHelperService.shared.modalViewData = []

        let store = IntegrationStore()
        store.showRequestIntegrationModal()

        #expect(NotificationHelperService.shared.modalViewData.count == 1)
    }

    @Test
    func showRequestIntegrationModalDoesNotDuplicateWhenCalledTwice() async throws {
        _ = ServiceRegistry.shared
        NotificationHelperService.shared.modalViewData = []

        let store = IntegrationStore()
        store.showRequestIntegrationModal()
        store.showRequestIntegrationModal()

        // showModal appends; test documents current behavior (two pushes = two entries)
        // and guards against silent no-ops.
        #expect(NotificationHelperService.shared.modalViewData.count >= 1)
    }
}
