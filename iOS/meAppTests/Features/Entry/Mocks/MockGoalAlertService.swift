import Foundation
@testable import meApp

@MainActor
final class MockGoalAlertService: GoalAlertServiceProtocol {
    var onNavigateToGoalSetting: (() -> Void)?
    var isOnDashboardTab: (() -> Bool)?

    var showGoalMetMessageError: Error?
    var checkSetGoalCardError: Error?

    private(set) var showGoalMetMessageCalls = 0
    private(set) var checkSetGoalCardCalls = 0
    private(set) var lastGoalMetWeight: Double?
    private(set) var lastEntryCount: Int?

    func showGoalMetMessage(currentWeight: Double) async {
        showGoalMetMessageCalls += 1
        lastGoalMetWeight = currentWeight
        _ = showGoalMetMessageError
    }

    func checkPendingGoalAlerts() async {}

    func resetGoalMetFlag() {}

    func checkSetGoalCard(entryCount: Int) async {
        checkSetGoalCardCalls += 1
        lastEntryCount = entryCount
        _ = checkSetGoalCardError
    }
}
