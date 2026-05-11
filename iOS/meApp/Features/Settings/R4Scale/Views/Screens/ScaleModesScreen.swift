//
//  ScaleModesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

/// A screen that allows users to configure scale modes and settings.
/// Supports both regular scale mode configuration and R4 scale setup workflows.
struct ScaleModesScreen: View {
    // MARK: - Environment Objects
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    
    // MARK: - Observed Objects
    @StateObject private var viewModel: ScaleModesViewModel
    
    // MARK: - Properties
    let scale: Device
    let isR4ScaleSetup: Bool
    /// When true, renders a sheet-style header (xmark) and dismisses the sheet instead of navigating back
    let isPresentedAsSheet: Bool
    private let lang = ScaleModesStrings.self

    // MARK: - Initializer
    init(scale: Device, isR4ScaleSetup: Bool = false, isWeighOnlyModeEnabledByOthers: Bool = false, isPresentedAsSheet: Bool = false) {
        self.scale = scale
        self.isR4ScaleSetup = isR4ScaleSetup
        self.isPresentedAsSheet = isPresentedAsSheet
        _viewModel = StateObject(wrappedValue: ScaleModesViewModel(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers))
    }

    // MARK: - Body
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: isR4ScaleSetup ? lang.r4scaleSetupTitle : lang.modeTitle,
                leadingContent: {
                    if isPresentedAsSheet {
                        Image(AppAssets.xmarkSmall)
                            .accessibilityLabel("Close")
                    } else {
                        Image(AppAssets.chevronLeft)
                            .accessibilityLabel("Back")
                    }
                },
                trailingContent: {
                    Group {
                        if isR4ScaleSetup {
                            Button(action: {
                                viewModel.openHelp()
                            }, label: {
                                Image(AppAssets.helpCircle)
                                    .accessibilityLabel("Help")
                            })
                        } else {
                            ButtonView(
                                text: CommonStrings.save.uppercased(),
                                type: .inlineTextPrimary,
                                size: .small,
                                isDisabled: !viewModel.hasModeChanges
                            ) {
                                    Task {
                                        await viewModel.handleScaleModeSave {
                                            if isPresentedAsSheet { dismiss() } else { router.navigateBack() }
                                        }
                                    }
                                }
                            .accessibilityLabel("Save scale mode preferences")
                        }
                    }
                },
                onLeadingTap: { if isPresentedAsSheet { dismiss() } else { router.navigateBack() } },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            if viewModel.isWeighOnlyModeEnabledByOthers {
                weightOnlyInfo()
                    .padding([.horizontal, .top], .spacingSM)
            }

            ScaleModesSelectionView(
                selectedMode: viewModel.modeValue,
                isHeartRateEnabled: viewModel.isHeartRateEnabled,
                isR4ScaleSetup: isR4ScaleSetup,
                onBIAButtonTap: {
                    viewModel.openBIAModel()
                },
                onValueChanged: { scaleMode, heartRateEnabled in
                    viewModel.updateModeValue(scaleMode)
                    viewModel.updateHeartRateEnabled(heartRateEnabled)
                }
            )
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .task {
            // Load scale mode data when the screen appears
            await viewModel.loadScaleModeData()
        }
    }
    
    private func weightOnlyInfo() -> some View {
        NoteBox {
            VStack(alignment: .leading, spacing: .spacingXS) {
                HStack {
                    AppIconView(icon: AppAssets.weightOnlyMode, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                    
                    Text(lang.weightOnlyBannerTitle)
                        .fontWeight(.bold)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textHeading)
                }
                
                Text(lang.temporarilyEnableAllBodyMetrics)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
            }
        }
    }
}

// MARK: - Preview
#Preview("Scale Modes Screen - Light") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "Preview Scale",
            deviceType: "scale"       
        )
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}

#Preview("Scale Modes Screen - Dark") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "Preview Scale",
            deviceType: "scale"       
        )
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
    .preferredColorScheme(.dark)
}

#Preview("R4 Scale Setup - Light") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-r4-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "R4 Scale Setup",
            deviceType: "scale"       
        ),
        isR4ScaleSetup: true
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}

// MARK: - ScaleModesViewModel
@MainActor
final class ScaleModesViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var accountService: AccountServiceProtocol

    private let scaleIdString: String

    /// Reads the current snapshot directly from the service — the single source of truth.
    private var deviceSnapshot: DeviceSnapshot? {
        scaleService.scales.first(where: { $0.id == scaleIdString })
    }

    @Published var modeValue: ScaleModes = .weightOnly
    @Published var isHeartRateEnabled: Bool = false
    @Published var isWeighOnlyModeEnabledByOthers: Bool = false

    // Track original values to detect changes
    private var originalModeValue: ScaleModes = .weightOnly
    private var originalIsHeartRateEnabled: Bool = false

    // Retry functionality
    private var retryCount: Int = 0
    private let maxRetries: Int = 2
    private let retryDelay: TimeInterval = 5.0

    private let tag = "ScaleModesViewModel"

    var hasModeChanges: Bool {
        return modeValue != originalModeValue || isHeartRateEnabled != originalIsHeartRateEnabled
    }

    init(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false) {
        self.scaleIdString = scale.id
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        setupFromSnapshot()
    }

    private func setupFromSnapshot() {
        if let preference = deviceSnapshot?.r4ScalePreference {
            modeValue = preference.shouldMeasureImpedance ? .allBodyMetrics : .weightOnly
            isHeartRateEnabled = preference.shouldMeasurePulse
        }
        originalModeValue = modeValue
        originalIsHeartRateEnabled = isHeartRateEnabled
    }

    func loadScaleModeData() async {
        setupFromSnapshot()
    }

    func updateModeValue(_ mode: ScaleModes) {
        modeValue = mode
        // If weight only mode is selected, disable heart rate
        if mode == .weightOnly {
            isHeartRateEnabled = false
        }
    }

    func updateHeartRateEnabled(_ enabled: Bool) {
        isHeartRateEnabled = enabled
    }

    func handleScaleModeSave(onSuccess: (() -> Void)? = nil) async {
        await performSaveOperation(onSuccess: onSuccess)
    }

// swiftlint:disable:next function_body_length
    private func performSaveOperation(onSuccess: (() -> Void)? = nil) async {
        guard let snapshot = deviceSnapshot, let preference = snapshot.r4ScalePreference else {
            logger.log(level: .error, tag: tag, message: "No R4 scale preference found for scale")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: "Unable to save scale mode settings"))
            return
        }

        var dto = preference.toDTO()
        let deviceId = scaleIdString
        let isConnected = snapshot.isConnected
        let broadcastId = snapshot.broadcastIdString ?? ""

        dto.shouldMeasureImpedance = (modeValue == .allBodyMetrics)
        dto.shouldMeasurePulse = isHeartRateEnabled && (modeValue == .allBodyMetrics)
        updateDisplayMetricsForHeartRate(dto: &dto)

        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))

        do {
            try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
            await scaleService.pushLocalChangesToServer()

            if isConnected {
                let result = await bluetoothService.updateAccount(broadcastId: broadcastId)
                switch result {
                case .success(let response):
                    switch response {
                    case .userSelectionInProgress:
                        dto.isSynced = false
                        try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                        notificationService.dismissLoader()
                        showUpdateAccountFailedAlert(onSuccess: onSuccess)
                        return

                    case .creationCompleted:
                        dto.isSynced = true
                        try await scaleService.updateScalePreference(deviceId, fromDTO: dto)

                    default:
                        dto.isSynced = false
                        try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                        throw BluetoothServiceError.updateProfileFailed(
                            NSError(
                                domain: "ScaleModesViewModel",
                                code: -1,
                                userInfo: [
                                    NSLocalizedDescriptionKey:
                                        "Scale update failed with response: \(response)"
                                ]
                            )
                        )
                    }
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update scale via Bluetooth: \(error.localizedDescription)")
                    dto.isSynced = false
                    try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                    throw error
                }
            } else {
                // Device offline — mark preference to sync on reconnect
                dto.isSynced = false
                try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                logger.log(level: .info, tag: tag, message: "Scale mode saved while device offline - will sync when device reconnects")
            }
            await loadScaleModeData()

            retryCount = 0
            originalModeValue = modeValue
            originalIsHeartRateEnabled = isHeartRateEnabled

            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ScaleModesStrings.preferencesSaved))
            onSuccess?()

        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save scale mode: \(error.localizedDescription)", data: error)
            notificationService.dismissLoader()

            if retryCount < maxRetries {
                retryCount += 1
                logger.log(level: .info, tag: tag, message: "Retrying save operation (attempt \(retryCount)/\(maxRetries))")

                try? await Task.sleep(nanoseconds: UInt64(retryDelay * 1_000_000_000))
                await performSaveOperation(onSuccess: onSuccess)
            } else {
                retryCount = 0
                showUpdateAccountFailedAlert(onSuccess: onSuccess)
            }
        }
    }

    /// Updates displayMetrics on the DTO for heart rate enable/disable.
    /// This is a pure data transformation — no async needed, no @Model access.
    private func updateDisplayMetricsForHeartRate(dto: inout R4ScalePreferenceDTO) {
        let heartRateKey = ScaleMetrics.config.first { $0.name == "Heart Rate" }?.key ?? "heartRate"
        let goalMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }

        // Add heart rate to display metrics if pulse is enabled and not already present
        if dto.shouldMeasurePulse && !dto.displayMetrics.contains(heartRateKey) {
            if let insertIndex = dto.displayMetrics.firstIndex(where: { goalMetricsKeys.contains($0) }) {
                dto.displayMetrics.insert(heartRateKey, at: insertIndex)
            } else {
                dto.displayMetrics.append(heartRateKey)
            }
        }

        // Remove heart rate from display metrics if pulse is disabled
        if !dto.shouldMeasurePulse {
            dto.displayMetrics.removeAll { $0 == heartRateKey }
        }
    }
    
    private func showUpdateAccountFailedAlert(onSuccess: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: AlertStrings.UpdateAccountFailedAlert.title,
            message: AlertStrings.UpdateAccountFailedAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.UpdateAccountFailedAlert.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: AlertStrings.UpdateAccountFailedAlert.tryAgainButton, type: .primary) { _ in
                    Task { [weak self] in
                        await self?.handleScaleModeSave(onSuccess: onSuccess)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func openHelp() {
         notificationService.showModal(ModalData(
            presentedView: AnyView(ModelNumberHelpModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    func openBIAModel() {
         notificationService.showModal(ModalData(
            presentedView: AnyView(BIAInfoModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
}
