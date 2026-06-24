import Combine
import Foundation
import SwiftUI

/// Store responsible for orchestrating the AppSync (scale setup) multi-step flow.
@MainActor
final class AppSyncSetupStore: ObservableObject {
    // MARK: - Dependencies

    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol
    @Injector var accountService: AccountServiceProtocol
    @Injector var permissionsService: PermissionsServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol

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
            if step == .appSync, step != currentStep {
                // Keep an invisible placeholder with full-page dimensions so
                // the page stack keeps its expected size.
                return AnyView(Color.clear)
            }

            return viewForStep(step, scaleItem: scaleItem)
        }
    }

    var dismissAction: (() -> Void)?

    // MARK: - Private

    private let tag = "AppSyncSetupStore"
    private var cancellables = Set<AnyCancellable>()
    private var scaleItem: DeviceItemInfo?

    // Strings
    let loaderLang = LoaderStrings.self
    let toastLang = ToastStrings.self

    // MARK: - Lifecycle

    init() {
        // Observe permission changes and update button state accordingly
        permissionsService.permissionsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
            }
            .store(in: &cancellables)
    }

    /// Call once the screen knows the SKU to prepare step flow.
    func configure(with sku: String) {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        // Pass original SKU to routes (not mapped), setup will save original SKU
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        // Resolve SKU → DeviceItemInfo (fallback to first element).
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES[0]
        scaleItem = resolved

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

        // Set setup in progress flag to prevent goal modals during setup
        bluetoothService.isSetupInProgress = true

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
        // Automatically request camera permission if not enabled
        if currentStep == .permissions {
            if !cameraEnabled {
                Task { await permissionsService.handlePermission(.camera) }
            }
        }
        isNextEnabled = cameraEnabled
    }

    // MARK: - Step → View mapping

    private func viewForStep(_ step: AppSyncSetupStep, scaleItem: DeviceItemInfo) -> AnyView {
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
                    // Closing the scanner should return to the previous setup instruction step.
                    self.moveToPreviousStep()
                },
                onManualEntry: {
                    self.moveToNextStep()
                },
                onScanned: { result in
                    // Move forward only for positive weight; otherwise return to the previous step.
                    if result.weight > 0 {
                        self.moveToNextStep()
                    } else {
                        self.moveToPreviousStep()
                    }
                }
            ))
        case .finish:
            let lang = ScaleSetupStrings.FinishViewStrings.self
            return AnyView(
                ScaleSetupFinishView(title: lang.title, description: lang.appSyncDescription, isAppSyncDeviceSetup: true)
                    .environmentObject(Theme.shared)
            )
        }
    }

    private func saveScale() { // swiftlint:disable:this function_body_length
        notificationService.showLoader(LoaderModel(text: loaderLang.saving))

        guard let scaleItem else {
            logger.log(level: .error, tag: tag, message: "saveScale - missing scale item")
            notificationService.dismissLoader()
            return
        }

        Task {
            defer { self.notificationService.dismissLoader() }
            guard let accountId = self.accountService.activeAccount?.accountId else {
                self.logger.log(level: .error, tag: self.tag, message: "saveScale - missing active account")
                return
            }
            logger.log(level: .info, tag: tag, message: "AppSync save scale started. accountId=\(accountId), sku=\(scaleItem.sku)")
            // Remove any existing device with the same SKU to avoid duplicates
            // Map SKU for comparison (e.g., 0022 -> 0383) so 0022 and 0383 are treated as duplicates
            do {
                let existingDevices = try await self.deviceService.getDevices()
                let scaleLookupSku = DeviceHelper.mapSkuForDisplay(scaleItem.sku)
                if let oldDevice = existingDevices.first(where: {
                    DeviceHelper.mapSkuForDisplay($0.sku ?? "") == scaleLookupSku
                }) {
                    do {
                        try await self.deviceService.deleteDevice(oldDevice.id, showToast: false)
                    } catch {
                        self.logger.log(
                            level: .error,
                            tag: self.tag,
                            message: "Failed to delete existing duplicate device before save: \(error.localizedDescription)"
                        )
                    }
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to remove existing device before saving: \(error.localizedDescription)")
            }

            do {
                let createdAt = DateTimeTools.getCurrentDatetimeIsoString()
                let newDevice = Device(
                    id: UUID().uuidString,
                    accountId: accountId,
                    sku: scaleItem.sku,
                    deviceName: scaleItem.productName,
                    deviceType: DeviceType.scale.rawValue,
                    createdAt: createdAt,
                    bathScale: BathScale(scaleType: DeviceSourceType.appsync.rawValue, bodyComp: scaleItem.bodyComp)
                )
                let response = try await self.deviceService.createDevice(newDevice, false)
                await self.deviceService.syncAllScalesWithRemote()
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "AppSync scale saved successfully. scaleId=\(response.id), sku=\(scaleItem.sku), accountId=\(accountId)"
                )
                ProductTypeStore.shared.selectLastAdded(.myWeight)

                // Post notification that scale was added
                NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)

                self.dismissAction?()
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save AppSync scale: sku=\(scaleItem.sku), accountId=\(accountId), error=\(error.localizedDescription)"
                )
                self.notificationService.showToast(ToastModel(message: ToastStrings.saveDeviceError))
            }
            self.bluetoothService.isSetupInProgress = false
        }
    }

    /// Returns `true` when the camera permission has already been granted.
    private func isCameraPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.CAMERA) == .ENABLED
    }

    /// Handles permission changes during the setup flow
    private func handlePermissionChange() {
        let showError = !isCameraPermissionEnabled()
        if showError {
            if currentStep == .appSync {
                // Navigate back to permissions screen if camera permission is revoked during app sync
                if let permissionStepIndex = steps.firstIndex(of: .permissions) {
                    currentStepIndex = permissionStepIndex
                }
            }
        }
    }

    /// Cleans up all subscriptions and resources when the view disappears
    func cleanUp() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        // Clear setup in progress flag when setup is dismissed
        bluetoothService.isSetupInProgress = false
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
