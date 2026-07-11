import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HKIntegrationHealthAccessViewModelTests {

    private func makeSUT() -> (sut: HKIntegrationHealthAccessViewModel, notification: MockNotificationHelperService) {
        TestDependencyContainer.reset()
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        return (HKIntegrationHealthAccessViewModel(), notification)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal presents a modal")
    func showHelpModalPresentsModal() {
        let (sut, notification) = makeSUT()

        sut.showHelpModal()

        #expect(notification.showModalCalls == 1)
        #expect(notification.modalViewData.count == 1)
    }

    // MARK: - showExitAlert — terminal states dismiss immediately

    @Test("showExitAlert dismisses without an alert when integration is complete")
    func showExitAlertIntegrationCompleteDismisses() {
        let (sut, notification) = makeSUT()
        var dismissed = 0

        sut.showExitAlert(state: .integrationComplete) { dismissed += 1 }

        #expect(dismissed == 1)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("showExitAlert dismisses without an alert when integration failed")
    func showExitAlertIntegrationFailedDismisses() {
        let (sut, notification) = makeSUT()
        var dismissed = 0

        sut.showExitAlert(state: .integrationFailed) { dismissed += 1 }

        #expect(dismissed == 1)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("showExitAlert dismisses without an alert on user conflict")
    func showExitAlertUserConflictDismisses() {
        let (sut, notification) = makeSUT()
        var dismissed = 0

        sut.showExitAlert(state: .userConflict) { dismissed += 1 }

        #expect(dismissed == 1)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("showExitAlert terminal state with nil dismiss handler does not crash")
    func showExitAlertTerminalNilHandler() {
        let (sut, notification) = makeSUT()

        sut.showExitAlert(state: .integrationComplete, dismiss: nil)

        #expect(notification.showAlertCalls == 0)
    }

    // MARK: - showExitAlert — confirmation states present an alert

    @Test("showExitAlert presents the exit confirmation alert for non-terminal states")
    func showExitAlertPresentsAlert() {
        let (sut, notification) = makeSUT()

        sut.showExitAlert(state: .permissionsNotAllowed) {}

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == AlertStrings.HKExitAlert.title)
        #expect(notification.alertData?.message == AlertStrings.HKExitAlert.message)
        #expect(notification.alertData?.buttons.map(\.title) == [
            AlertStrings.HKExitAlert.exitButton,
            AlertStrings.HKExitAlert.cancelButton
        ])
    }

    @Test("showExitAlert primary (exit) button invokes the dismiss handler")
    func showExitAlertPrimaryButtonDismisses() {
        let (sut, notification) = makeSUT()
        var dismissed = 0

        sut.showExitAlert(state: .permissionsAllowed) { dismissed += 1 }
        notification.alertData?.buttons.first?.action(nil)

        #expect(dismissed == 1)
    }

    @Test("showExitAlert cancel button does not invoke the dismiss handler")
    func showExitAlertCancelButtonKeepsFlow() {
        let (sut, notification) = makeSUT()
        var dismissed = 0

        sut.showExitAlert(state: .permissionsAllowed) { dismissed += 1 }
        notification.alertData?.buttons.last?.action(nil)

        #expect(dismissed == 0)
    }
}
