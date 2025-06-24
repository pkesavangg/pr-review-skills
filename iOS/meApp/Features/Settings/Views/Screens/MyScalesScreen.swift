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
    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
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
                            allowWholeNumbers: true
                        ),
                        value: Binding(
                            get: { scaleStore.addScaleForm.modelNumber },
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
                            // TODO: ADD action
                        }
                    )
                    .padding(.bottom, .spacingSM)
                    ButtonView(
                        text: lang.cantFindModelNumber,
                        type: .textPrimary,
                        size: .large,
                        isDisabled: false,
                        action: {
                            // TODO: ADD action
                        }
                    )
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingLG)
                
                if !scaleStore.scales.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        
                        Text(lang.myScales)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.leading)
                            .padding(.horizontal, .spacingSM)
                        
                        ForEach(scaleStore.scales, id: \.id) { scale in
                            ScaleItemView(
                                scaleIcon: Image(AppAssets.meLogoDark),
                                modelNumber: scale.sku ?? "----",
                                scaleName: scale.deviceName ?? lang.unknownScale,
                                status: .connected,
                                onTap: {
                                    // TODO: Add Action
                                }
                            )
                            .padding(.horizontal, .spacingSM)
                            
                            Divider()
                        }
                    }
                }
            }
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

import SwiftUI

// MARK: ModelNumberHelpModalView
/// A view that displays a simple help modal for model number help (placeholder)
struct ModelNumberHelpModalView: View {
    let onClose: () -> Void
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme

    var body: some View {
        GeometryReader { geometry in
            VStack {
                VStack(alignment: .leading){
                    Text("Check the back of your scale for a sticker with your four-digit model number.")
                        .fontOpenSans(.body2)
                        
                    Text("For example, if you have a 0375 Bluetooth Scale, your sticker will show the URL")
                        .fontOpenSans(.body2)
                    Text("greatergoods.com/0375.")
                        .fontOpenSans(.body2)
                }
                .padding(.horizontal,.spacingXS)
                .padding(.vertical, .spacingLG)
            }
            .padding(.horizontal, .spacingXS)
            .padding(.vertical, .spacingXS)
            .background(theme.backgroundSecondary)
            .cornerRadius(.radiusXL)
            .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 5)
            .frame(width: geometry.size.width * 0.85)
            .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
        .background(.clear)
    }
}

// MARK: - Preview
#Preview {
    ModelNumberHelpModalView(onClose: {})
        .environmentObject(Theme.shared)
}
