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
    @Published var currentStepIndex: Int = 0 {
        didSet { currentStep = steps[currentStepIndex] }
    }
    @Published private(set) var currentStep: AppSyncSetupStep = .intro
    @Published var isNextEnabled: Bool = true // Reserved for future validation rules
    
    /// Ordered list of steps. Updated once `configure(with:)` is called.
    @Published private(set) var steps: [AppSyncSetupStep] = AppSyncSetupStep.allCases
    
    /// Lazily generated list of views corresponding to `steps`.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { viewForStep($0, scaleItem: scaleItem) }
    }
    
    var dismissAction: DismissAction?
    
    // MARK: - Private
    private let tag = "AppSyncSetupStore"
    private var cancellables = Set<AnyCancellable>()
    private var scaleItem: ScaleItemInfo?
    
    // Strings
    let loaderLang = LoaderStrings.self
    let toastLang = ToastStrings.self
    
    // MARK: - Lifecycle
    init() { }
    
    /// Call once the screen knows the SKU to prepare step flow.
    func configure(with sku: String) {
        // Resolve SKU → ScaleItemInfo (fallback to first element).
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES[0]
        self.scaleItem = resolved
        
        // Determine steps based on body-composition support.
        if resolved.bodyComp {
            steps = AppSyncSetupStep.allCases
        } else {
            steps = AppSyncSetupStep.allCases.filter { $0 != .addInfo }
        }
        
        // Reset navigation indices.
        currentStepIndex = 0
        currentStep = steps.first ?? .intro
    }
    
    // MARK: - Navigation helpers
    func moveToNextStep() {
        guard currentStepIndex < steps.count - 1 else {
            // Finished – invoke completion callback
            self.saveScale()
            return
        }
        currentStepIndex += 1
    }
    
    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
    }
    
    // MARK: - Step → View mapping
    private func viewForStep(_ step: AppSyncSetupStep, scaleItem: ScaleItemInfo) -> AnyView {
        switch step {
        case .intro:
            return AnyView(ScaleSetupIntroView(scale: scaleItem))
        case .permissions:
            return AnyView(PermissionListView(setupType: .appSync))
        case .activateScale:
            let lang = AppSyncStrings.ActivateYourScaleViewStrings.self
            return AnyView(ScaleInstructionView(title: lang.title, description: lang.description, boldWords: lang.boldWords))
        case .addInfo:
            return AnyView(AddInfoView())
        case .timeToWeighIn:
            let lang = AppSyncStrings.WeighInTimeStrings.self
            return AnyView(ScaleInstructionView(title: lang.title, description: lang.description, boldWords: lang.boldWords))
        case .appSync:
            return AnyView(AppSyncScannerView(
                showManualEntryButton: false,
                onClose: {
                    self.moveToNextStep()
                },
                onManualEntry: {
                    self.moveToNextStep()
                },
                onScanned: { result in
                    self.moveToNextStep()
                }
            )) // TODO: replace with actual AppSync progress screen
        case .finish:
            let lang = AppSyncStrings.FinishViewStrings.self
            return AnyView(
                ScaleSetupFinishView(title: lang.title, description: lang.description, isAppSyncScaleSetup: true)
                    .environmentObject(Theme.shared)
            )
        }
    }
    
    // MARK: - Exit / Help
    
    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        if currentStep == .finish {
            self.dismissAction?()
            return
        }
        
        let alertLang = AlertStrings.ExitSetupAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.dismissAction?()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in }
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
    
    private func saveScale() {
        notificationService.showLoader(LoaderModel(
            text: loaderLang.saving
        ))
        // TODO: Implement actual scale saving logic here.
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
            self.notificationService.dismissLoader()
            self.dismissAction?()
        }
    }
}
