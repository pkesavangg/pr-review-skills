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
final class GoalAlertService: GoalAlertServiceProtocol, ObservableObject {
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

    /// Callback to check if we're currently on Dashboard tab (set by BottomTabBarViewModel)
    /// Returns true if Dashboard tab is selected, false otherwise
    var isOnDashboardTab: (() -> Bool)?

    // MARK: - Internal State

    private(set) var isShowingAlert: Bool = false
    private let kv = KvStorageService.shared
    private let tag = "GoalAlertService"

    private let alertStrings = AlertStrings.self
    /// Stores pending alert weight when triggered on landing/loading screen
    private var pendingAlertWeight: Double?

    // MARK: - Public API

    /// Evaluates whether a goal-related alert should be presented based on the
    /// latest weight.
    /// - Parameter currentWeight: Latest weight value **in stored units** (tenths of lbs).
    func showGoalMetMessage(currentWeight: Double) async { // swiftlint:disable:this cyclomatic_complexity
        guard !isShowingAlert else { return }
        guard !bluetoothService.isSetupInProgress else { return }
        guard let account = accountService.activeAccount,
              let goalSettings = account.goalSettings,
              let goalType = goalSettings.goalType,
              let goalWeight = goalSettings.goalWeight else { return }

        let storageKey = goalAlertStorageKey(for: account.accountId)
        guard let alertAlreadyShown = (kv.getValue(forKey: storageKey) as? Bool) else {
            kv.setValue(false, forKey: storageKey)
            return
        }
        guard !alertAlreadyShown else { return }
        let hasMetGoal: Bool = {
            switch goalType {
            case .none:
                return false
            case .gain:
                return currentWeight >= goalWeight
            case .lose:
                return currentWeight <= goalWeight
            case .maintain:
                return currentWeight != goalWeight
            }
        }()

        guard hasMetGoal else { return }
        logger.log(
            level: .info,
            tag: tag,
            message: "Goal alert condition met. accountId=\(account.accountId), goalType=\(goalType.rawValue), currentWeight=\(currentWeight), goalWeight=\(goalWeight)" // swiftlint:disable:this line_length
        )

        // If we're on landing/loading screen, store pending alert to show later
        guard isOnDashboardTab != nil else {
            pendingAlertWeight = currentWeight
            return
        }

        // Persist flag so the alert is not re-shown in the same session until reset
        kv.setValue(true, forKey: storageKey)

        switch goalType {
        case .none:
            return
        case .maintain:
            await presentGoalLeaveAlert()
        case .gain, .lose:
            await presentGoalMetAlert()
        }
    }
    
    /// Checks for pending goal alerts and shows them if conditions are met.
    /// Call this when user enters bottom tab bar context.
    func checkPendingGoalAlerts() async {
        guard let pendingWeight = pendingAlertWeight else { return }
        pendingAlertWeight = nil
        await showGoalMetMessage(currentWeight: pendingWeight)
    }

    // MARK: - Helpers

    func resetGoalMetFlag() {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        kv.setValue(false, forKey: goalAlertStorageKey(for: accountId))
        logger.log(level: .info, tag: tag, message: "Reset goal-met flag. accountId=\(accountId)")
    }

    private func goalAlertStorageKey(for accountId: String) -> String {
        return "\(accountId)-goalMetFlag"
    }

    /// Checks if the "Set a Goal" card should be shown when user has 3+ entries but no goal set.
    /// - Parameter entryCount: The current number of entries for the user
    func checkSetGoalCard(entryCount: Int) async {
        guard !isShowingAlert else { return }
        guard isOnDashboardTab?() == true else { return }
        guard !bluetoothService.isSetupInProgress else { return }
        guard let account = accountService.activeAccount else { return }

        if let goalSettings = account.goalSettings,
           let goalType = goalSettings.goalType,
           goalType != .none {
            return
        }

        guard entryCount >= 3 else { return }

        let storageKey = KvStorageKeys.setAGoalModalFlagKey(for: account.accountId)
        if let hasBeenShown = kv.getValue(forKey: storageKey) as? Bool, hasBeenShown {
            return
        }

        await presentSetGoalCard(accountId: account.accountId)
    }

    private func presentSetGoalCard(accountId: String) async {
        isShowingAlert = true

        let storageKey = KvStorageKeys.setAGoalModalFlagKey(for: accountId)
        kv.setValue(true, forKey: storageKey)

        let setGoalModalDelay = 3.0
        try? await Task.sleep(nanoseconds: UInt64(setGoalModalDelay * 1_000_000_000))

        guard isOnDashboardTab?() == true else {
            isShowingAlert = false
            return
        }
        guard accountService.activeAccount != nil else {
            isShowingAlert = false
            return
        }

        let cardView = SetAGoalCardView(
            onClose: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                self.isShowingAlert = false
            },
            onSetGoal: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                self.isShowingAlert = false
                self.handleNewGoalAction()
            }
        )

        let modal = ModalData(
            presentedView: AnyView(cardView),
            backdropDismiss: false
        )

        logger.log(level: .info, tag: tag, message: "Presenting set-a-goal modal card. accountId=\(accountId)")
        notificationService.showModal(modal)
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
        logger.log(level: .info, tag: tag, message: "Goal alert action selected: navigate to new goal")
        notificationService.dismissAlert()
        onNavigateToGoalSetting?()
        isShowingAlert = false
    }

    private func handleMaintainAction() async {
        defer { isShowingAlert = false }

        guard let account = accountService.activeAccount,
              let goalWeight = account.goalSettings?.goalWeight
        else {
            logger.log(level: .error, tag: tag, message: "Maintain-goal action failed: missing active account or goal weight")
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
            logger.log(level: .success, tag: tag, message: "Successfully created maintain goal", data: "\(maintainGoal)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to create maintain goal", data: error.localizedDescription)
        }
        resetGoalMetFlag()
        notificationService.dismissAlert()
    }
}
