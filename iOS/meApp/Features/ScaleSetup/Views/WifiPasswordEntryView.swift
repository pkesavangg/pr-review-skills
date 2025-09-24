//
//  WifiPasswordEntryView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 14/07/25.
//


import SwiftUI

struct WifiPasswordEntryView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var store: BtWifiScaleSetupStore
    let wifiDetail: WifiDetails
    @State private var focusedField: FocusField?
    let lang = WifiScreenStrings.self
    let commonLang = CommonStrings.self
    var labels = InputFieldLabels.self
    
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading){
                        VStack(alignment: .leading, spacing: .spacingXS){
                            Text(lang.enterPasswordTitle)
                                .fontOpenSans(.heading4)
                                .fontWeight(.bold)
                                .foregroundColor(theme.textBody)
                            (
                                Text(lang.enterPasswordSubtitlePrefix)
                                + Text(store.networkForm.ssid.value).fontWeight(.bold)
                                + Text(lang.enterPasswordSubtitleSuffix)
                            )
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                        }
                        .padding(.bottom, .spacingLG)
                        
                        AppInputField(
                            config: TextInputConfig(
                                label: labels.password,
                                placeholder: lang.passwordPlaceholder,
                                inputType: .password,
                                submitLabel: .done,
                                errorMessage: store.networkForm.getError(for: store.networkForm.password),
                                isDisabled: store.networkForm.networkHasNoPassword
                            ),
                            value: $store.networkForm.password.value,
                            focusedField: $focusedField
                        ) {
                            hideKeyboard()
                        }
                        
                        CustomToggleView(isOn: $store.networkForm.networkHasNoPassword, text: lang.noPasswordToggle)
                            .padding(.top, 0)
                    }
                    .padding(.top, .spacingLG)
                    .navigationBarBackButtonHidden(true)
                }
            }
            .background(theme.backgroundSecondary)
            .scrollDismissesKeyboard(.interactively)
        }
    }
}

#Preview{
    let store = BtWifiScaleSetupStore()
    store.configure(with: "0412")
    return WifiPasswordEntryView(wifiDetail: WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"))
        .environmentObject(store)
}
