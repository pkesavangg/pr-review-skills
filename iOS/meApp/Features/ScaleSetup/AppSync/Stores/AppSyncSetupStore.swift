import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the AppSync (scale setup) multi-step flow.
@MainActor
final class AppSyncSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    @Injector private var accountService: AccountService
    // Permissions
    @Injector private var permissionsService: PermissionsService

    // MARK: - Public state
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    @Published private(set) var currentStep: AppSyncSetupStep = .intro
    @Published var isNextEnabled: Bool = true // Dynamically updated based on permission state

    /// Ordered list of steps. Updated once `configure(with:)` is called.
    @Published private(set) var steps: [AppSyncSetupStep] = AppSyncSetupStep.allCases

    /// Lazily builds the views for each step. The `AppSyncScannerView` is
    /// constructed **only** when the current step is `.appSync` so that the
    /// camera permission dialog is requested at the correct time.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }

        return steps.map { step in
            // Defer building `AppSyncScannerView` until the user actually
            // navigates to that page. Prior pages will render an `EmptyView`
            // placeholder with the same layout footprint, preventing early
            // evaluation of the scanner – and therefore the camera prompt.
            if step == .appSync && step != currentStep {
                // Keep an invisible placeholder with full-page dimensions so
                // the page stack keeps its expected size.
                return AnyView(Color.clear)
            }

            return viewForStep(step, scaleItem: scaleItem)
        }
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
    init() {
        // Observe permission changes and update button state accordingly
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
    }

    /// Call once the screen knows the SKU to prepare step flow.
    func configure(with sku: String) {
        // Resolve SKU → ScaleItemInfo (fallback to first element).
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES[0]
        self.scaleItem = resolved

        // Determine steps based on body-composition support.
        // Build the base set of steps first (depends on body-composition support).
        let baseSteps: [AppSyncSetupStep] = {
            if resolved.bodyComp {
                return AppSyncSetupStep.allCases
            } else {
                return AppSyncSetupStep.allCases.filter { $0 != .addInfo }
            }
        }()

        // Skip the permission screen entirely when the camera permission is
        // already enabled. This lets users who have granted the permission in
        // advance jump straight to the next step without an unnecessary stop.
        let cameraPermissionGranted = permissionsService.getPermissionState(.CAMERA) == .ENABLED
        steps = cameraPermissionGranted ? baseSteps.filter { $0 != .permissions } : baseSteps

        // Reset navigation indices.
        currentStepIndex = 0
        currentStep = steps.first ?? .intro

        // Evaluate initial button state based on current permissions
        updateNextEnabled()
    }

    // MARK: - Navigation helpers

    func moveToNextStep() {
        var nextIndex = currentStepIndex + 1

        // Skip the permissions step if the camera permission is already enabled.
        while nextIndex < steps.count, steps[nextIndex] == .permissions, isCameraPermissionEnabled() {
            nextIndex += 1
        }

        // Reached the end – save and exit.
        guard nextIndex < steps.count else {
            saveScale()
            return
        }

        currentStepIndex = nextIndex
    }

    func moveToPreviousStep() {
        var previousIndex = currentStepIndex - 1

        // Skip the permissions step when navigating backwards if it is already satisfied.
        while previousIndex >= 0, steps[previousIndex] == .permissions, isCameraPermissionEnabled() {
            previousIndex -= 1
        }

        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }

    // MARK: - Exit / Help

    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
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
            presentedView: AnyView(HelpModalView(skuToNavigate: scaleItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }

    // MARK: - Validation Helpers
    /// Updates `isNextEnabled` based on the current step and camera permission state.
    private func updateNextEnabled() {
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }

        let cameraEnabled = permissionsService.getPermissionState(.CAMERA) == .ENABLED
        isNextEnabled = cameraEnabled
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
            ))
        case .finish:
            let lang = ScaleSetupStrings.FinishViewStrings.self
            return AnyView(
                ScaleSetupFinishView(title: lang.title, description: lang.appSyncDescription, isAppSyncScaleSetup: true)
                    .environmentObject(Theme.shared)
            )
        }
    }

    private func saveScale() {
        notificationService.showLoader(LoaderModel(text: loaderLang.saving))

        guard let scaleItem else {
            notificationService.dismissLoader()
            return
        }

        Task {
            defer { self.notificationService.dismissLoader() }
            guard let accountId = self.accountService.activeAccount?.accountId else {
                return
            }
            // Remove any existing device with the same SKU to avoid duplicates
            do {
                let existingDevices = try await self.scaleService.getDevices()
                if let oldDevice = existingDevices.first(where: { $0.sku == scaleItem.sku }) {
                    try? await self.scaleService.deleteDevice(oldDevice.id, showToast: false)
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to remove existing device before saving: \(error.localizedDescription)")
            }

            do {
                let newDevice = Device(
                    id: UUID().uuidString,
                    accountId: accountId,
                    sku: scaleItem.sku,
                    deviceName: scaleItem.productName,
                    deviceType: DeviceType.scale.rawValue,
                    bathScale: BathScale(scaleType: ScaleSourceType.appsync.rawValue, bodyComp: scaleItem.bodyComp)
                )
                let response = try await self.scaleService.createDevice(newDevice)
                await self.scaleService.pushLocalChangesToServer()
                logger.log(level: .info, tag: tag, message: "Scale saved successfully with ID: \(response.id) \(scaleItem.sku)")
                
                // Post notification that scale was added
                NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
                
                self.dismissAction?()
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
                self.notificationService.showToast(ToastModel(message: ToastStrings.saveScaleError))
            }
        }
    }

    /// Returns `true` when the camera permission has already been granted.
    private func isCameraPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.CAMERA) == .ENABLED
    }

    deinit {
      cancellables.forEach { $0.cancel() }
      cancellables.removeAll()
    }
}
