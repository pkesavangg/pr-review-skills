//
//  DisplayMetricsScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct DisplayMetricsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: DisplayMetricsViewModel
    let lang = ScaleModesStrings.self
    
    let scale: Device
    let isWeighOnlyModeEnabledByOthers: Bool
    
    init(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false) {
        self.scale = scale
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        _viewModel = StateObject(wrappedValue: DisplayMetricsViewModel(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers))
    }
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: DisplayMetricStrings.displayMetricsTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: {
                            Task {
                                await viewModel.saveDisplayMetrics()
                                router.navigateBack()
                            }
                        }
                    )
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {
                    // TODO: ADD Action
                },
                canShowBorder: true
            )
            
            List {
                bannerSection()
                descriptionSection()
                
                // Body Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { viewModel.metrics },
                        set: { viewModel.updateMetrics($0) }
                    ),
                    onValueChanged: { viewModel.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = viewModel.metrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        viewModel.updateMetrics(updatedMetrics)
                        viewModel.updateDisplayMetricsValue()
                    },
                    showIcon: true
                )
                
                // Progress Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { viewModel.progressMetrics },
                        set: { viewModel.updateProgressMetrics($0) }
                    ),
                    onValueChanged: { viewModel.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = viewModel.progressMetrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        viewModel.updateProgressMetrics(updatedMetrics)
                        viewModel.updateDisplayMetricsValue()
                    },
                    showIcon: false
                )
            }
            .scrollContentBackground(.hidden)
            .listStyle(.insetGrouped)
            .scrollIndicators(.hidden)
            .environment(\.editMode, .constant(.active))
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .task {
            await viewModel.loadDisplayMetricsData()
        }
    }
    
    // MARK: - Sections as Functions
    private func bannerSection() -> some View {
        Section {
            if viewModel.showWeightOnlyBanner || viewModel.showWeightOnlyInfo || viewModel.showHeartRateBanner {
                VStack(spacing: .spacingSM) {
                    if viewModel.showWeightOnlyBanner { weightOnlyBanner() }
                    if viewModel.showWeightOnlyInfo { weightOnlyInfo() }
                    if viewModel.showHeartRateBanner { heartRateBanner() }
                }
            }
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }
    
    private func descriptionSection() -> some View {
        Section {
            Text(lang.displayMetricsDescription)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }
    
    // MARK: - Banner Components
    
    private func weightOnlyBanner() -> some View {
        let commonLang = CommonStrings.self
        return NoteBox {
            HStack(spacing: .spacingSM) {
                StatusRowView(
                    iconName: AppAssets.weightOnlyMode,
                    label: lang.weightOnlyLabel,
                    statusText: viewModel.isWeightOnlyModeOn ? commonLang.on.uppercased() : commonLang.off.uppercased()
                )
                .fontWeight(.regular)
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {
                    // Navigate to scale modes screen where user can change their mode
                    router.navigate(to: .scaleModes(scale: scale))
                })
            }
        }
    }
    
    private func weightOnlyInfo() -> some View {
        NoteBox {
            VStack(alignment: .leading, spacing: .spacingXS) {
                HStack() {
                    AppIconView(icon: AppAssets.weightOnlyMode, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                    
                    Text(lang.weightOnlyBannerTitle)
                        .fontWeight(.bold)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textHeading)
                }
                
                Text(lang.weightOnlyBannerDescription)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
            }
        }
    }
    
    private func heartRateBanner() -> some View {
        let commonLang = CommonStrings.self
        let iconAndLabelColor = viewModel.isHeartRateOn ? theme.statusIconPrimary : theme.statusIconSecondary
        
        return NoteBox {
            HStack(spacing: .spacingSM) {
                StatusRowView(
                    iconName: AppAssets.heartIcon,
                    label: commonLang.heartRateLabel,
                    statusText: viewModel.isHeartRateOn ? commonLang.on.uppercased() : commonLang.off.uppercased(),
                    foregroundColor: iconAndLabelColor
                )
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {
                    Task {
                        await viewModel.updateHeartRateEnabled(!viewModel.isHeartRateEnabled)
                    }
                })
            }
        }
    }
}

// MARK: - DisplayMetricsViewModel
@MainActor
final class DisplayMetricsViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector var logger: LoggerService
    @Injector var accountService: AccountService
    
    @Published var scale: Device
    @Published var metrics: [ScaleMetricSetting] = []
    @Published var progressMetrics: [ScaleMetricSetting] = []
    @Published var displayMetricsValue: String = ""
    
    // Banner states
    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    @Published var isWeightOnlyModeOn: Bool = false
    @Published var isHeartRateOn: Bool = false
    @Published var isHeartRateEnabled: Bool = false
    
    private let isWeighOnlyModeEnabledByOthers: Bool
    private let tag = "DisplayMetricsViewModel"
    
    init(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false) {
        self.scale = scale
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        setupInitialValues()
    }
    
    private func setupInitialValues() {
        // Load initial metrics
        loadDisplayMetrics()
        
        // Setup banner states based on scale preferences
        updateBannerStates()
    }
    
    func loadDisplayMetricsData() async {
        // Refresh scale data from service
        do {
            if let updatedScale = try await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                scale = updatedScale
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load scale data: \(error)")
        }
        
        // Load display metrics and update banner states
        loadDisplayMetrics()
        updateBannerStates()
    }
    
    private func loadDisplayMetrics() {
        guard let preference = scale.r4ScalePreference else {
            // Default metrics if no preference
            metrics = ScaleMetrics.bodyMetrics
            progressMetrics = ScaleMetrics.progressMetrics
            updateDisplayMetricsValue()
            return
        }
        
        let shouldMeasureImpedance = preference.shouldMeasureImpedance
        let displayMetricsKeys = preference.displayMetrics
        
        // Get available body metrics based on impedance setting
        let availableBodyMetrics = shouldMeasureImpedance ? 
            ScaleMetrics.bodyMetrics : 
            ScaleMetrics.bodyMetrics.filter { $0.key == "bmi" }
        
        // Order body metrics: enabled first (in displayMetrics order), then disabled (in ScaleMetrics order)
        metrics = orderMetrics(
            availableMetrics: availableBodyMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        // Order progress metrics the same way
        progressMetrics = orderMetrics(
            availableMetrics: ScaleMetrics.progressMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        updateDisplayMetricsValue()
    }
    
    private func orderMetrics(
        availableMetrics: [ScaleMetricSetting],
        displayMetricsKeys: [String]
    ) -> [ScaleMetricSetting] {
        var orderedMetrics: [ScaleMetricSetting] = []
        
        // First, add enabled metrics in the order they appear in displayMetrics
        for key in displayMetricsKeys {
            if let metric = availableMetrics.first(where: { $0.key == key }) {
                var enabledMetric = metric
                enabledMetric.isEnabled = true
                orderedMetrics.append(enabledMetric)
            }
        }
        
        // Then, add disabled metrics in their original ScaleMetrics order
        for metric in availableMetrics {
            if !displayMetricsKeys.contains(metric.key) {
                var disabledMetric = metric
                disabledMetric.isEnabled = false
                orderedMetrics.append(disabledMetric)
            }
        }
        
        return orderedMetrics
    }
    
    private func updateBannerStates() {
        guard let preference = scale.r4ScalePreference else {
            showWeightOnlyBanner = false
            showWeightOnlyInfo = false
            showHeartRateBanner = false
            isWeightOnlyModeOn = false
            isHeartRateOn = false
            isHeartRateEnabled = false
            return
        }
        
        let shouldMeasureImpedance = preference.shouldMeasureImpedance
        let shouldMeasurePulse = preference.shouldMeasurePulse
        
        // Weight-only banner logic
        showWeightOnlyBanner = !shouldMeasureImpedance // Current user is in weight-only mode
        showWeightOnlyInfo = isWeighOnlyModeEnabledByOthers // Show info when weight-only is enabled by others
        
        // Heart rate banner logic
        showHeartRateBanner = !shouldMeasurePulse && shouldMeasureImpedance // Heart rate is off but body metrics are on
        
        // State values
        isWeightOnlyModeOn = !shouldMeasureImpedance
        isHeartRateOn = shouldMeasurePulse
        isHeartRateEnabled = shouldMeasurePulse
    }
    
    func updateMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Check if this is a toggle operation (enabled state changed) or a reorder operation
        let oldEnabledKeys = Set(metrics.filter { $0.isEnabled }.map { $0.key })
        let newEnabledKeys = Set(newMetrics.filter { $0.isEnabled }.map { $0.key })
        
        let isToggleOperation = oldEnabledKeys != newEnabledKeys
        let isReorderOperation = !isToggleOperation
        
        if isReorderOperation {
            // For reorder operations, just update the metrics directly without re-ordering
            metrics = newMetrics
            updateDisplayMetricsValue()
            return
        }
        
        // For toggle operations, just update the enabled state without changing order
        metrics = newMetrics
        updateDisplayMetricsValue()
    }
    
    func updateProgressMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Check if this is a toggle operation (enabled state changed) or a reorder operation
        let oldEnabledKeys = Set(progressMetrics.filter { $0.isEnabled }.map { $0.key })
        let newEnabledKeys = Set(newMetrics.filter { $0.isEnabled }.map { $0.key })
        
        let isToggleOperation = oldEnabledKeys != newEnabledKeys
        let isReorderOperation = !isToggleOperation
        
        if isReorderOperation {
            // For reorder operations, just update the metrics directly without re-ordering
            progressMetrics = newMetrics
            updateDisplayMetricsValue()
            return
        }
        
        // For toggle operations, just update the enabled state without changing order
        progressMetrics = newMetrics
        updateDisplayMetricsValue()
    }
    
    func updateDisplayMetricsValue() {
        let allMetrics = metrics + progressMetrics
        let enabledMetrics = allMetrics.filter { $0.isEnabled }
        displayMetricsValue = enabledMetrics.map { $0.key }.joined(separator: ",")
    }
    
    func updateHeartRateEnabled(_ enabled: Bool) async {
        guard let preference = scale.r4ScalePreference else { return }
        
        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        
        do {
            // Update the preference
            preference.shouldMeasurePulse = enabled
            
            // Handle heart rate display metrics
            await updateDisplayMetricsForHeartRate(preference: preference)
            
            // Save to local database
            try await scaleService.updateScalePreference(scale.id, preference)
            await scaleService.pushLocalChangesToServer()
            
            // Update the scale via Bluetooth if connected
            if scale.isConnected == true {
                let result = await bluetoothService.updateAccount(on: scale, preference: preference)
                switch result {
                case .success(_):
                    logger.log(level: .info, tag: tag, message: "Heart rate setting updated successfully via Bluetooth")
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update heart rate setting via Bluetooth: \(error.localizedDescription)")
                    throw error
                }
            }
            
            // Update local state
            isHeartRateEnabled = enabled
            isHeartRateOn = enabled
            updateBannerStates()
            
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: "Heart rate setting updated successfully"))
            
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update heart rate setting: \(error.localizedDescription)", data: error)
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: "Failed to update heart rate setting"))
        }
    }
    
    private func updateDisplayMetricsForHeartRate(preference: R4ScalePreference) async {
        let heartRateKey = ScaleMetrics.config.first { $0.name == "Heart Rate" }?.key ?? "heartRate"
        let goalMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }
        
        // Add heart rate to display metrics if pulse is enabled and not already present
        if preference.shouldMeasurePulse && !preference.displayMetrics.contains(heartRateKey) {
            // Find insertion point (before goal metrics if they exist)
            if let insertIndex = preference.displayMetrics.firstIndex(where: { goalMetricsKeys.contains($0) }) {
                preference.displayMetrics.insert(heartRateKey, at: insertIndex)
            } else {
                preference.displayMetrics.append(heartRateKey)
            }
        }
        
        // Remove heart rate from display metrics if pulse is disabled
        if !preference.shouldMeasurePulse {
            preference.displayMetrics.removeAll { $0 == heartRateKey }
        }
    }
    
    func saveDisplayMetrics() async {
        guard let preference = scale.r4ScalePreference else { return }
        
        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        
        do {
            let shouldMeasureImpedance = preference.shouldMeasureImpedance
            
            if shouldMeasureImpedance {
                // Normal mode: Update display metrics from current UI state
                let bodyEnabledKeys = metrics.filter { $0.isEnabled }.map { $0.key }
                let progressEnabledKeys = progressMetrics.filter { $0.isEnabled }.map { $0.key }
                
                // Combine body and progress metrics while maintaining the order from the UI
                preference.displayMetrics = bodyEnabledKeys + progressEnabledKeys
                
            } else {
                // Weight-only mode: Only modify BMI, preserve all other metrics in their existing positions
                var updatedDisplayMetrics = preference.displayMetrics
                
                // Check if BMI is enabled in the UI
                let isBMIEnabled = metrics.first(where: { $0.key == "bmi" })?.isEnabled ?? false
                
                if isBMIEnabled {
                    // Add BMI if not already present
                    if !updatedDisplayMetrics.contains("bmi") {
                        // Find a good position to insert BMI (at the beginning of body metrics)
                        let progressMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }
                        if let firstProgressIndex = updatedDisplayMetrics.firstIndex(where: { progressMetricsKeys.contains($0) }) {
                            updatedDisplayMetrics.insert("bmi", at: firstProgressIndex)
                        } else {
                            updatedDisplayMetrics.append("bmi")
                        }
                    }
                } else {
                    // Remove BMI if present
                    updatedDisplayMetrics.removeAll { $0 == "bmi" }
                }
                
                // Update progress metrics normally
                let progressEnabledKeys = progressMetrics.filter { $0.isEnabled }.map { $0.key }
                
                // Remove old progress metrics and add new ones in order
                let progressMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }
                updatedDisplayMetrics.removeAll { progressMetricsKeys.contains($0) }
                updatedDisplayMetrics.append(contentsOf: progressEnabledKeys)
                
                preference.displayMetrics = updatedDisplayMetrics
            }
            
            // Save to local database
            try await scaleService.updateScalePreference(scale.id, preference)
            await scaleService.pushLocalChangesToServer()
            
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.displayMetricsSaved))
            
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save display metrics: \(error.localizedDescription)", data: error)
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorSavingDisplayMetrics))
        }
    }
}

#Preview {
    DisplayMetricsScreen(scale: Device(
        id: "preview-scale-id",
        accountId: "preview-account",
        sku: "0412",
        deviceName: "Preview Scale",
        deviceType: "scale"
    ))
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}
