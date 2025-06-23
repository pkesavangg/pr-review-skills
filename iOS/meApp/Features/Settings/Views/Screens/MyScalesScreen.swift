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
    @StateObject private var addScaleForm = AddScaleForm()
    @FocusState private var focusedField: FocusField?
    
    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }
    
    let lang = MyScaleStrings.self
    
    var body: some View {
        VStack(alignment: .leading){
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
                .padding(.top, .spacingLG)
                
                VStack(alignment: .center){
                    AppInputField(
                        config: TextInputConfig(
                            label: lang.modelNumber,
                            inputType: .number,
                            errorMessage: addScaleForm.error,
                            focusField: .modelNumber,
                            customIcon: AppAssets.helpCircle
                        ),
                        value: Binding(
                            get: { addScaleForm.modelNumber },
                            set: { addScaleForm.setModelNumber($0) }
                        ),
                        focusedField: focusBinding
                    ) {
                        addScaleForm.markAsDirty()
                    }
                    .onChange(of: addScaleForm.modelNumber) { newValue in
                        let filtered = newValue.filter { $0.isNumber }
                        if filtered.count > 4 {
                            addScaleForm.modelNumber = String(filtered.prefix(4))
                        }
                    }
                    .onChange(of: focusedField) { newValue in
                        if newValue != .modelNumber {
                            addScaleForm.markAsDirty()
                        }
                    }
                    
                    ButtonView(
                        text: CommonStrings.submit,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !addScaleForm.isValid,
                        action: {
                            addScaleForm.markAsDirty()
                            // add submission logic here
                        }
                    )
                    .padding(.top, 24)
                    .padding(.bottom,21)
                    
                    ButtonView(
                        text: lang.cantFindModelNumber,
                        type: .textPrimary,
                        size: .large,
                        isDisabled: false,
                        action: {
                            // Help action
                        }
                    )
                    
                }
                .padding(.vertical, .spacingLG)
                
                VStack (alignment: .leading){
                    Text(lang.myScales)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.leading)
                    
                    ForEach(scaleStore.scales, id: \.id) { scale in
                        ScaleItemView(
                            scaleIcon: Image(AppAssets.meLogoDark),
                            modelNumber: scale.sku ?? "----",
                            scaleName: scale.deviceName ?? lang.unknownScale,
                            status: .connected,
                            onTap: {
                                // Handle scale tap (e.g., show details)
                            }
                        )
                        Divider()
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .onTapGesture {
            hideKeyboard()
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onTapGesture {
            focusedField = nil
        }
    }
}

#Preview {
    MyScalesScreen()
}
