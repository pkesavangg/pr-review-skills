//
//  ScaleNameScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ScaleNameScreen : View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var scaleStore = ScaleStore()
    let scale: Device
    let lang = ScaleSettingsStrings.self
    let commonLang = CommonStrings.self
    
    @State private var editedName: String = ""
    @State private var focusedField: FocusField? = nil
    @State private var errorMessage: String? = nil
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.scaleName,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: commonLang.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: editedName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || editedName == (scale.nickname ?? scale.deviceName ?? ""),
                        action: {
                            Task {
                                await scaleStore.saveScaleName(editedName)
                                if scaleStore.errorMessage == nil {
                                    router.navigateBack()
                                } else {
                                    errorMessage = scaleStore.errorMessage
                                }
                            }
                        }
                    ))
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            VStack(spacing: .spacingMD) {
                AppInputField(
                    config: TextInputConfig(
                        label: lang.scaleName,
                        placeholder: lang.scaleName,
                        inputType: .text,
                        errorMessage: errorMessage,
                        focusField: .scaleName
                    ),
                    value: $editedName,
                    focusedField: $focusedField
                ) {
                    // Handle commit action if needed
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingMD)
                
                Spacer()
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            // Load the scale in the store
            Task {
                await scaleStore.loadScale(scale)
            }
            // Initialize the edited name with current scale name
            editedName = scale.nickname ?? scale.deviceName ?? ""
        }
    }
    

}

#Preview{
    let mockDevice = Device(
        id: "1",
        accountId: "demo-account",
        sku: "0412",
        deviceName: "AccuCheck Verve Smart Scale"
    )
    ScaleNameScreen(scale: mockDevice)
}
