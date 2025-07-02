import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the AppSync (scale setup) multi-step flow.
@MainActor
final class AppSyncSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService

    // MARK: - Public state
    @Published var currentStepIndex: Int = AppSyncSetupStep.info.index {
        didSet { currentStep = steps[currentStepIndex] }
    }
    @Published private(set) var currentStep: AppSyncSetupStep = .info
    @Published var isNextEnabled: Bool = true // Reserved for future validation rules

    
    var dismissAction: DismissAction?

    /// Callback fired when the entire setup finishes successfully.
    var onSetupSuccess: (() -> Void)?

    // MARK: - Private
    private let tag = "AppSyncSetupStore"
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Steps configuration
    let steps: [AppSyncSetupStep] = AppSyncSetupStep.allCases

    // MARK: - Navigation helpers
    func moveToNextStep() {
        guard currentStepIndex < steps.count - 1 else {
            // Finished – invoke completion callback
            onSetupSuccess?()
            return
        }
        currentStepIndex += 1
    }

    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
    }

    // MARK: - Exit / Help

    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        let alert = AlertModel(
            title: AlertStrings.SignupExitAlert.title,
            message: AlertStrings.SignupExitAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.SignupExitAlert.exitButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.dismissAction?()
                },
                AlertButtonModel(title: AlertStrings.SignupExitAlert.goBackButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Shows the generic Help modal used across the app.
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView {
                self.notificationService.dismissModal()
            })
        ))
    }
} 
