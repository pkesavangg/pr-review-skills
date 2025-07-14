//
//  WifiPasswordEntryView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 14/07/25.
//


import SwiftUI

struct WifiPasswordEntryView: View {
    @Environment(\.appTheme) private var theme
    let wifiDetail: WifiDetails
    @State private var focusedField: FocusField?
    @State private var networkHasNoPassword: Bool = false
    @State private var password: String = ""
    let lang = WifiScreenStrings.self
    let commonLang = CommonStrings.self
    var labels = InputFieldLabels.self
    
    var body: some View {
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
                            + Text(wifiDetail.ssid ?? "").fontWeight(.bold)
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
                            errorMessage: nil,
                            isDisabled: networkHasNoPassword
                        ),
                        value: $password,
                        focusedField: $focusedField
                    ) {
                        hideKeyboard()
                    }
                    
                    CustomToggleView(isOn: $networkHasNoPassword, text: lang.noPasswordToggle)
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

#Preview{
    WifiPasswordEntryView(wifiDetail: WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"))
}
