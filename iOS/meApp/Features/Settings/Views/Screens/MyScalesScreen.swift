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
    private enum ActiveSheet: Identifiable {
        case scaleList
        case setupFlow(ScaleItemInfo)

        var id: String {
            switch self {
            case .scaleList:
                return "scaleList"
            case .setupFlow(let scale):
                // SKU uniquely identifies a scale
                return scale.sku
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
                
                VStack(alignment: .center, spacing: 0){
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
                        value: Binding(
                            get: { scaleStore.addScaleForm.modelNumberValue },
                            set: { scaleStore.addScaleForm.setModelNumber($0) }
                        ),
                        focusedField: focusBinding
                    )
                    .padding(.bottom, .spacingMD)
                    ButtonView(
                        text: CommonStrings.submit,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !scaleStore.addScaleForm.isValid,
                        action: {
                            // Fetch the corresponding scale and trigger setup flow sheet.
                            if let scale = SCALES.first(where: { $0.sku == scaleStore.addScaleForm.modelNumberValue }) {
                                // Clear focus & reset form
                                focusedField = nil
                                hideKeyboard()

                                activeSheet = .setupFlow(scale)
                                // Optional: reset the form for next entry
                                scaleStore.resetForm()
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
                                activeSheet = .setupFlow(scale)
                            }
                        }
                    case .setupFlow(let scale):
                        if scale.setupType == .appSync {
                            AppSyncSetupScreen(sku: scale.sku)
                                .interactiveDismissDisabled(true)
                        } else {
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
                                scaleName: scale.deviceName ?? lang.unknownScale,
                                status: .connected,
                                onTap: {
                                    router.navigate(to: .scaleSettings(scale: scale, scaleType: .bluetoothR4))
                                }
                            )
                            .padding(.horizontal, .spacingSM)
                            
                            Divider()
                        }
                    }
                }
            }
        }
        .onAppear{
            scaleStore.fetchScales()
        }
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
