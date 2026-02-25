import Foundation

@MainActor
protocol GoalAlertServiceProtocol: AnyObject {
    var onNavigateToGoalSetting: (() -> Void)? { get set }
    var isOnDashboardTab: (() -> Bool)? { get set }

    func showGoalMetMessage(currentWeight: Double) async
    func resetGoalMetFlag()
    func checkSetGoalCard(entryCount: Int) async
}

