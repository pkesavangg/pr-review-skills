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
    @FocusState private var focusedField: FocusField?

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }
    
    let lang = MyScaleStrings.self

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
            //.padding(.horizontal, .spacingSM)
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
