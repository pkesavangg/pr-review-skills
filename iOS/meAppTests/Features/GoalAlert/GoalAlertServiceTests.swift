import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct GoalAlertServiceTests {

    @Test("showGoalMetMessage initializes per-account goal flag when key is missing")
    func showGoalMetMessageInitializesFlagWhenMissing() async {
        let (sut, _, _, _, kv, account) = makeSUT(goalType: .gain, goalWeight: 150)

        await sut.showGoalMetMessage(currentWeight: 160)

        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        #expect((kv.getValue(forKey: key) as? Bool) == false)
    }

    @Test("showGoalMetMessage gain-goal met shows alert and NEW GOAL triggers navigation")
    func showGoalMetMessageGainShowsAlertAndNavigates() async {
        let (sut, notification, _, _, kv, account) = makeSUT(goalType: .gain, goalWeight: 150)
        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(false, forKey: key)

        var navigateCalls = 0
        sut.onNavigateToGoalSetting = { navigateCalls += 1 }

        await sut.showGoalMetMessage(currentWeight: 155)

        #expect(notification.showAlertCalls == 1)
        guard let alert = notification.alertData else {
            Issue.record("Expected goal-met alert")
            return
        }
        #expect(alert.buttons.count == 2)

        alert.buttons[0].action(nil)

        #expect(notification.dismissAlertCalls == 1)
        #expect(navigateCalls == 1)
        #expect(sut.isShowingAlert == false)
        #expect((kv.getValue(forKey: key) as? Bool) == true)
    }

    @Test("showGoalMetMessage maintain-goal drift shows leave alert and NO keeps user on current flow")
    func showGoalMetMessageMaintainShowsLeaveAlertNoAction() async {
        let (sut, notification, _, _, kv, account) = makeSUT(goalType: .maintain, goalWeight: 150)
        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(false, forKey: key)

        await sut.showGoalMetMessage(currentWeight: 151)

        #expect(notification.showAlertCalls == 1)
        guard let alert = notification.alertData else {
            Issue.record("Expected goal-leave alert")
            return
        }
        #expect(alert.buttons.count == 2)

        alert.buttons[0].action(nil)

        #expect(sut.isShowingAlert == false)
    }

    @Test("showGoalMetMessage maintain-goal drift YES action triggers navigation")
    func showGoalMetMessageMaintainYesNavigates() async {
        let (sut, notification, _, _, kv, account) = makeSUT(goalType: .maintain, goalWeight: 150)
        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(false, forKey: key)

        var navigateCalls = 0
        sut.onNavigateToGoalSetting = { navigateCalls += 1 }

        await sut.showGoalMetMessage(currentWeight: 151)

        guard let alert = notification.alertData else {
            Issue.record("Expected goal-leave alert")
            return
        }
        alert.buttons[1].action(nil)

        #expect(notification.dismissAlertCalls == 1)
        #expect(navigateCalls == 1)
        #expect(sut.isShowingAlert == false)
    }

    @Test("showGoalMetMessage MAINTAIN action creates maintain goal, resets flag, and dismisses")
    func showGoalMetMessageMaintainActionCreatesGoalAndResetsFlag() async {
        let (sut, notification, accountService, _, kv, account) = makeSUT(goalType: .lose, goalWeight: 150)
        accountService.createGoalResult = .success(account)

        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(false, forKey: key)

        await sut.showGoalMetMessage(currentWeight: 149)

        guard let alert = notification.alertData else {
            Issue.record("Expected goal-met alert")
            return
        }
        alert.buttons[1].action(nil)

        try? await Task.sleep(nanoseconds: 120_000_000)

        #expect(accountService.createGoalCalls == 1)
        #expect(accountService.lastCreatedGoal?.goalType == .maintain)
        #expect(accountService.lastCreatedGoal?.goalWeight == 150)
        #expect(accountService.lastCreatedGoal?.initialWeight == 150)
        #expect(notification.dismissAlertCalls == 1)
        #expect((kv.getValue(forKey: key) as? Bool) == false)
        #expect(sut.isShowingAlert == false)
    }

    @Test("showGoalMetMessage returns early when bluetooth setup is in progress")
    func showGoalMetMessageReturnsWhenBluetoothSetupInProgress() async {
        let (sut, notification, _, bluetooth, kv, account) = makeSUT(goalType: .gain, goalWeight: 150)
        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(false, forKey: key)
        bluetooth.isSetupInProgress = true

        await sut.showGoalMetMessage(currentWeight: 160)

        #expect(notification.showAlertCalls == 0)
    }

    @Test("resetGoalMetFlag updates stored flag to false for active account")
    func resetGoalMetFlagResetsStoredFlag() {
        let (sut, _, _, _, kv, account) = makeSUT(goalType: .gain, goalWeight: 150)
        let key = GoalAlertTestFixtures.goalMetFlagKey(accountId: account.accountId)
        kv.setValue(true, forKey: key)

        sut.resetGoalMetFlag()

        #expect((kv.getValue(forKey: key) as? Bool) == false)
    }

    @Test("checkSetGoalCard does not show card if not on dashboard tab")
    func checkSetGoalCardSkipsWhenNotOnDashboardTab() async {
        let (sut, notification, _, _, _, _) = makeSUT(goalType: GoalType.none)
        sut.isOnDashboardTab = { false }

        await sut.checkSetGoalCard(entryCount: 3)

        #expect(notification.showModalCalls == 0)
    }

    @Test("checkSetGoalCard shows card when eligible and records shown flag")
    func checkSetGoalCardShowsModalWhenEligible() async {
        let (sut, notification, _, _, kv, account) = makeSUT(goalType: GoalType.none, setGoalModalDelay: 0)
        sut.isOnDashboardTab = { true }

        await sut.checkSetGoalCard(entryCount: 3)

        let key = KvStorageKeys.setAGoalModalFlagKey(for: account.accountId)
        #expect(notification.showModalCalls == 1)
        #expect((kv.getValue(forKey: key) as? Bool) == true)
    }

    @Test("checkSetGoalCard re-checks dashboard callback before presenting and cancels when tab changed")
    func checkSetGoalCardCancelsWhenDashboardTabChangesDuringDelay() async {
        let (sut, notification, _, _, _, _) = makeSUT(goalType: GoalType.none, setGoalModalDelay: 0.05)
        var isOnDashboard = true
        sut.isOnDashboardTab = { isOnDashboard }

        let task = Task {
            await sut.checkSetGoalCard(entryCount: 3)
        }
        isOnDashboard = false
        await task.value

        #expect(notification.showModalCalls == 0)
        #expect(sut.isShowingAlert == false)
    }
}

@MainActor
private func makeSUT(
    goalType: GoalType? = GoalType.none,
    goalWeight: Double? = nil,
    setGoalModalDelay: TimeInterval = 0
) -> (
    GoalAlertService,
    MockNotificationHelperService,
    MockAccountService,
    MockBluetoothService,
    MockKvStorageService,
    Account
) {
    let notification = MockNotificationHelperService()
    let accountService = MockAccountService()
    let bluetoothService = MockBluetoothService()
    let logger = MockLoggerService()
    let kv = MockKvStorageService()

    let account = GoalAlertTestFixtures.makeAccount(
        id: "goal-account-\(UUID().uuidString)",
        goalType: goalType,
        goalWeight: goalWeight,
        initialWeight: goalWeight
    )
    accountService.activeAccount = account

    let sut = GoalAlertService(
        notificationService: notification,
        accountService: accountService,
        bluetoothService: bluetoothService,
        logger: logger,
        kv: kv,
        setGoalModalDelay: setGoalModalDelay
    )
    sut.isOnDashboardTab = { true }

    return (sut, notification, accountService, bluetoothService, kv, account)
}
