//
//  AddAndEditScalesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI

struct MyScalesScreen: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject private var scaleStore = ScaleStore()
    let lang = MyScaleStrings.self

    @FocusState private var focusedField: FocusField?
    @State private var shouldMaintainKeyboardFocus = false

    // Consolidated sheet presentation state
    private enum ActiveSheet: Identifiable, Equatable {
        case scaleList
        case setupFlow(ScaleItemInfo)

        var id: String {
            switch self {
            case .scaleList:
                return "scaleList"
            case .setupFlow(let scale):
                return scale.sku
            }
        }

        // Custom Equatable conformance is required because `ScaleItemInfo`
        // itself may not conform to Equatable or may need to be compared using specific logic.
        // This implementation allows SwiftUI to compare two ActiveSheet values properly.
        //
        // It's especially needed for:
        // - .onChange(of: activeSheet), which requires the observed type to conform to Equatable
        // - ensuring SwiftUI detects sheet transitions and doesn't suppress updates
        static func == (lhs: ActiveSheet, rhs: ActiveSheet) -> Bool {
            switch (lhs, rhs) {
            case (.scaleList, .scaleList):
                return true
            case let (.setupFlow(lhsScale), .setupFlow(rhsScale)):
                return lhsScale.sku == rhsScale.sku
            default:
                return false
            }
        }
    }

    @State private var activeSheet: ActiveSheet?

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    private func scaleIcon(for sku: String?) -> Image {
        // Map SKU for display (e.g., 0022 -> 0383) for SCALES lookup
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku ?? "")
        let imagePath = SCALES.first { $0.sku == lookupSku }?.imgPath
            ?? bpmCatalogItem(forEnteredCode: sku ?? "")?.imgPath
            ?? AppAssets.meLogoDark
        return Image(imagePath)
    }

    /// Determines the scale type based on the scale's SKU and other properties
    private func determineScaleType(for scale: Device) -> ScaleType {
        return ScaleTypeHelper.determineScaleType(for: scale)
    }

    /// Centralised handler that encapsulates duplicate-check & navigation logic for a selected **scale**.
    /// - Parameters:
    ///   - scale: The `ScaleItemInfo` that the user selected / submitted.
    ///   - clearUI: When `true` the keyboard is dismissed and the add-scale form reset before navigation (used by the submit button path).
    private func handleScaleSelection(_ scale: ScaleItemInfo, clearUI: Bool = false) {
        // Closure executed once all pre-flight checks pass.
        let proceed = {
            if clearUI {
                focusedField = nil
                scaleStore.resetForm()
            }
            activeSheet = .setupFlow(scale)
            hideKeyboard()
        }

        switch scale.setupType {
        case .appSync:
            // Prevent adding duplicate AppSync scales unless the user explicitly confirms.
            // Map SKU for comparison (e.g., 0022 -> 0383) so 0022 and 0383 are treated as duplicates
            let scaleLookupSku = DeviceHelper.mapSkuForDisplay(scale.sku)
            let isDuplicate = scaleStore.scales.contains {
                DeviceHelper.mapSkuForDisplay($0.sku ?? "") == scaleLookupSku
            }
            if isDuplicate {
                scaleStore.handleDuplicateScale(sku: scale.sku, onPair: proceed)
            } else {
                proceed()
            }
        default:
            // For other setup types we can continue immediately.
            proceed()
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            NavbarHeaderView(
                title: lang.addEditScales,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.addAScale)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    Text(lang.enterModelNumber)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingLG)
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .center, spacing: 0) {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.modelNumber.lowercased(),
                            inputType: .metric,
                            errorMessage: scaleStore.addScaleForm.getError(for: .modelNumber),
                            focusField: .modelNumber,
                            customIcon: AppAssets.helpCircle,
// swiftlint:disable:next vertical_parameter_alignment_on_call
                                onCustomIconTap: {
                                    focusedField = nil
                                    hideKeyboard()
                                    scaleStore.openHelp()
                                },
                            maxLength: 4,
                            allowWholeNumbers: true,
                            showPrefixZero: true
                        ),
                        value: $scaleStore.addScaleForm.modelNumber.value,
                        focusedField: focusBinding
                    )
                    .padding(.bottom, .spacingMD)
                    ButtonView(
                        text: CommonStrings.submit,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !scaleStore.addScaleForm.isValid
                    ) {
                            // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
                            let enteredValue = scaleStore.addScaleForm.modelNumberValue
                            let lookupSku = DeviceHelper.mapSkuForDisplay(enteredValue)

                            // Find the scale or BPM matching the SKU.
                            let scaleInfo = SCALES.first(where: { $0.sku == lookupSku })
                                ?? bpmCatalogItem(forEnteredCode: enteredValue)
                            guard let scaleInfo else { return }

                            // Create a modified scale info with original SKU for navigation
                            // Pass original SKU to routes (not mapped), setup will save original SKU
                            let scaleWithOriginalSku = ScaleItemInfo(
                                productName: scaleInfo.productName,
                                sku: enteredValue, // Use original SKU
                                imgPath: scaleInfo.imgPath,
                                setupType: scaleInfo.setupType,
                                bodyComp: scaleInfo.bodyComp
                            )

                            handleScaleSelection(scaleWithOriginalSku, clearUI: true)
                        }
                    .padding(.bottom, .spacingSM)

                    ButtonView(
                        text: lang.cantFindModelNumber,
                        type: .textPrimary,
                        size: .large,
                        isDisabled: false
                    ) {
                            focusedField = nil
                            hideKeyboard()
                            activeSheet = .scaleList
                        }
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingLG)
                .sheet(
                    item: $activeSheet,
                    onDismiss: {
                        scaleStore.updateSetupInProgressStatus(false)
                    },
                    content: { sheet in
                    switch sheet {
                    case .scaleList:
                        ChooseYourScaleView { scale in
                            // Delay so the scale list sheet dismisses before presenting the next one
                            Task { @MainActor in
                                try? await Task.sleep(nanoseconds: 250_000_000)
                                handleScaleSelection(scale)
                            }
                        }
                    case .setupFlow(let scale):
                        switch scale.setupType {
                        case .appSync:
                            AppSyncSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        case .lcbt:
                            A6ScaleSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        case .btWifiR4:
                            BtWifiScaleSetupScreen(sku: scale.sku, discoveredScale: nil, discoveryEvent: nil, isReconnect: false, isDuplicated: false)
                                .interactiveDismissDisabled(true)
                        case .bluetooth:
                            BluetoothScaleSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        case .espTouchWifi, .wifi:
                            WifiScaleSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        case .babyScale:
                            BabyScaleSetupScreen(sku: scale.sku)
                        case .bpm:
                            BpmSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        }
                    }
                    }
                )
                .onChange(of: activeSheet) { _, newSheet in
                    // Observe changes to the activeSheet state.
                    // This is used to track whether a setup flow is being shown,
                    // and toggle the Bluetooth setup in-progress flag accordingly.
                    switch newSheet {
                    case .setupFlow:
                        // A setup flow sheet is being presented → start setup tracking
                        scaleStore.updateSetupInProgressStatus(true)
                        focusedField = nil
                        hideKeyboard()
                    default:
                        break
                    }
                }

                if !scaleStore.scales.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(lang.myScales)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.leading)
                            .padding(.horizontal, .spacingSM)

                        ForEach(scaleStore.scales, id: \.id) { scale in
                            let scaleType = determineScaleType(for: scale)
                            ScaleItemView(
                                scaleIcon: scaleIcon(for: scale.sku),
                                modelNumber: DeviceHelper.mapSkuForDisplay(scale.sku ?? "----"),
                                scaleName: scale.nickname ?? scale.deviceName ?? lang.unknownScale,
                                status: scaleStore.determineConnectionStatus(for: scale),
                                onTap: {
                                    if scaleType == .bpm {
                                        router.navigate(to: .bpmDeviceSettings(device: scale))
                                    } else {
                                        router.navigate(to: .scaleSettings(scale: scale, scaleType: scaleType))
                                    }
                                },
                                scaleType: scaleType
                            )

                            Divider()
                        }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onTapGesture {
            if !shouldMaintainKeyboardFocus {
                focusedField = nil
                hideKeyboard()
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if focusedField == .modelNumber {
                if newPhase != .active {
                    shouldMaintainKeyboardFocus = true
                } else if shouldMaintainKeyboardFocus {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 100_000_000)
                        focusedField = .modelNumber
                        shouldMaintainKeyboardFocus = false
                    }
                }
            }
        }
    }

}

#Preview {
    MyScalesScreen()
    .environmentObject(ScaleStore())
}
