//
//  AddAndEditScalesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI

struct MyScalesScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject var scaleStore = ScaleStore()
    let lang = MyScaleStrings.self
    
    @FocusState private var focusedField: FocusField?
    
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
        let imagePath = SCALES.first(where: { $0.sku == (sku ?? "") })?.imgPath ?? AppAssets.meLogoDark
        return Image(imagePath)
    }
    
    /// Determines the scale type based on the scale's SKU and other properties
    private func determineScaleType(for scale: Device) -> ScaleType {
        return ScaleTypeHelper.determineScaleType(for: scale)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing:0){
            NavbarHeaderView(
                title: lang.addEditScales,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            ScrollView (showsIndicators: false){
                VStack(alignment: .leading, spacing: .spacingXS){
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
                
                VStack(alignment: .center, spacing: 0) {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.modelNumber,
                            inputType: .metric,
                            errorMessage: scaleStore.addScaleForm.getError(for: .modelNumber),
                            focusField: .modelNumber,
                            customIcon: AppAssets.helpCircle,
                            onCustomIconTap: { scaleStore.openHelp() },
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
                        isDisabled: !scaleStore.addScaleForm.isValid,
                        action: {
                            // Find the scale matching the entered model number.
                            guard let scale = SCALES.first(where: { $0.sku == scaleStore.addScaleForm.modelNumberValue }) else { return }
                            
                            // Proceed to setup: clear UI state and show setup flow.
                            let proceed = {
                                focusedField = nil
                                hideKeyboard()
                                activeSheet = .setupFlow(scale)
                                scaleStore.resetForm()
                            }
                            
                            switch scale.setupType {
                            case .appSync:
                                // If scale is already paired, show alert; else proceed directly.
                                let isDuplicate = scaleStore.scales.contains { $0.sku == scale.sku }
                                if isDuplicate {
                                    scaleStore.handleDuplicateScale(sku: scale.sku, onPair: proceed)
                                } else {
                                    proceed()
                                }
                                
                            default:
                                // For other setup types, always proceed without this check it handled in the setup.
                                proceed()
                            }
                        }
                    )
                    .padding(.bottom, .spacingSM)
                    
                    ButtonView(
                        text: lang.cantFindModelNumber,
                        type: .textPrimary,
                        size: .large,
                        isDisabled: false,
                        action: {
                            focusedField = nil
                            hideKeyboard()
                            activeSheet = .scaleList
                        }
                    )
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingLG)
                .sheet(item: $activeSheet) { sheet in
                    switch sheet {
                    case .scaleList:
                        ChooseYourScaleView { scale in
                            // Delay so the scale list sheet dismisses before presenting the next one
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                let isDuplicate = scaleStore.scales.contains { $0.sku == scale.sku }
                                let proceed = {
                                    activeSheet = .setupFlow(scale)
                                }
                                if isDuplicate {
                                    scaleStore.handleDuplicateScale(sku: scale.sku, onPair: proceed)
                                } else {
                                    proceed()
                                }
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
                            BtWifiScaleSetupScreen(sku: scale.sku, discoveredScale: nil, discoveryEvent: nil)
                                .interactiveDismissDisabled(true)
                        default:
                            // TODO: Handle other setup types
                            VStack(spacing: .spacingMD) {
                                Text("Setup flow coming soon")
                                    .fontOpenSans(.heading4)
                                Text("Selected scale: \(scale.productName)")
                                    .fontOpenSans(.body2)
                            }
                            .padding()
                        }
                    }
                }
                .onChange(of: activeSheet) { _, newSheet in
                    // Observe changes to the activeSheet state.
                    // This is used to track whether a setup flow is being shown,
                    // and toggle the Bluetooth setup in-progress flag accordingly.
                    switch newSheet {
                    case .setupFlow:
                        // A setup flow sheet is being presented → start setup tracking
                        scaleStore.bluetoothService.isSetupInProgress = true
                    default:
                        // Either the sheet was dismissed or it's a non-setup sheet → stop setup tracking
                        scaleStore.bluetoothService.isSetupInProgress = false
                        scaleStore.bluetoothService.resumeSmartScan(clearOnlyPairing: false)
                        Task {
                            await scaleStore.bluetoothService.resyncAndScan()
                        }
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
                            ScaleItemView(
                                scaleIcon: scaleIcon(for: scale.sku),
                                modelNumber: scale.sku ?? "----",
                                scaleName: scale.nickname ?? scale.deviceName ?? lang.unknownScale,
                                status: .connected,
                                onTap: {
                                    let scaleType = determineScaleType(for: scale)
                                    router.navigate(to: .scaleSettings(scale: scale, scaleType: scaleType))
                                }
                            )
                            .padding(.horizontal, .spacingSM)
                            
                            Divider()
                        }
                    }
                }
            }
        }
        .onAppear(perform: {
            scaleStore.fetchScales()
        })
        .onDisappear {
            scaleStore.resetForm()
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onTapGesture {
            focusedField = nil
            hideKeyboard()
        }
    }
}

#Preview {
    MyScalesScreen()
}
