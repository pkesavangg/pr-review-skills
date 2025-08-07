//
//  GoalAlertService.swift
//  meApp
//
//  Created by AI Assistant on 06/08/25.
//

import Foundation
import SwiftUI

/// Service responsible for displaying goal-related alerts (goal reached / drifting away)
/// and handling the user actions for those alerts (set new goal, maintain goal, etc.).
///
/// The behaviour mirrors the legacy `goalalert.service.ts` from the Ionic/Angular
/// application but is adapted for SwiftUI + the existing notification helper layer.
@MainActor
final class GoalAlertService: ObservableObject {
    static let shared = GoalAlertService()
    private init() {}

    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var accountService: AccountService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Public Callback
    /// Assigned by the UI layer (e.g. `BottomTabBarViewModel`) so the service can
    /// request navigation to the Goal Setting screen when the user taps **NEW GOAL** / **YES**.
    var onNavigateToGoalSetting: (() -> Void)?

    // MARK: - Internal State
    private(set) var isShowingAlert: Bool = false
    private let kv = KvStorageService.shared
    private let tag = "GoalAlertService"
    
    private let alertStrings = AlertStrings.self

    // MARK: - Public API
    /// Evaluates whether a goal-related alert should be presented based on the
    /// latest weight.
    /// - Parameter currentWeight: Latest weight value **in stored units** (tenths of lbs).
    func showGoalMetMessage(currentWeight: Double) async {
        guard !isShowingAlert else { return }
        guard !bluetoothService.isSetupInProgress else { return }
        guard let account = accountService.activeAccount,
              let goalSettings = account.goalSettings,
              let goalType = goalSettings.goalType,
              let goalWeight = goalSettings.goalWeight else { return }

        let storageKey = goalAlertStorageKey(for: account.accountId)
        let alertAlreadyShown = (kv.getValue(forKey: storageKey) as? Bool) ?? false
        guard !alertAlreadyShown else { return }
        let hasMetGoal: Bool = {
            switch goalType {
            case .gain:
                return currentWeight >= goalWeight
            case .lose:
                return currentWeight <= goalWeight
            case .maintain:
                return currentWeight != goalWeight
            }
        }()

        guard hasMetGoal else { return }

        // Persist flag so the alert is not re-shown in the same session until reset
        kv.setValue(true, forKey: storageKey)

        switch goalType {
        case .maintain:
            await presentGoalLeaveAlert()
        case .gain, .lose:
            await presentGoalMetAlert()
        }
    }

    // MARK: - Helpers
    func resetGoalMetFlag() {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        kv.setValue(false, forKey: goalAlertStorageKey(for: accountId))
    }
    
    private func goalAlertStorageKey(for accountId: String) -> String {
        return "\(accountId)-goalMetFlag"
    }

    // MARK: - Alert Builders
    private func presentGoalMetAlert() async {
        isShowingAlert = true
        let alert = AlertModel(
            title: alertStrings.GoalMetAlert.header,
            message: alertStrings.GoalMetAlert.message,
            buttons: [
                // NEW GOAL ➜ navigate to Goal Setting screen
                AlertButtonModel(title: alertStrings.GoalMetAlert.newGoal, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.handleNewGoalAction()
                },
                // MAINTAIN ➜ create a *maintain* goal automatically
                AlertButtonModel(title: alertStrings.GoalMetAlert.maintain, type: .primary) { [weak self] _ in
                    Task { await self?.handleMaintainAction() }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func presentGoalLeaveAlert() async {
        isShowingAlert = true
        let alert = AlertModel(
            title: "",
            message: alertStrings.GoalLeaveAlert.message,
            buttons: [
                AlertButtonModel(title: alertStrings.GoalLeaveAlert.no, type: .secondary) { [weak self] _ in
                    self?.isShowingAlert = false
                },
                AlertButtonModel(title: alertStrings.GoalLeaveAlert.yes, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.handleNewGoalAction()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Button Handlers
    private func handleNewGoalAction() {
        notificationService.dismissAlert()
        onNavigateToGoalSetting?()
        isShowingAlert = false
    }

    private func handleMaintainAction() async {
        defer { isShowingAlert = false }

        guard let account = accountService.activeAccount,
              let goalWeight = account.goalSettings?.goalWeight else {
            notificationService.dismissAlert()
            return
        }

        // Build a *maintain* goal payload using the current goal weight.
        let maintainGoal = Goal(
            type: .maintain,
            goalWeight: Int(goalWeight),
            initialWeight: Int(goalWeight),
            goalType: .maintain
        )

        do {
            _ = try await accountService.createGoal(maintainGoal)
            logger.log(level: .info, tag: tag, message: "Successfully created maintain goal", data: "\(maintainGoal)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to create maintain goal", data: error.localizedDescription)
        }
        resetGoalMetFlag()
        notificationService.dismissAlert()
    }
}
