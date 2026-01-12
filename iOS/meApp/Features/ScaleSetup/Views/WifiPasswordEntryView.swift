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
    @State private var keyboardHeight: CGFloat = 0
    let lang = BtWifiScaleSetupStrings.WifiScreenStrings.self
    let commonLang = CommonStrings.self
    let isScaleSetup: Bool
    var labels = InputFieldLabels.self
    
    // Calculate bottom padding for ScrollView content to prevent overlap with footer buttons
    private var scrollViewBottomPadding: CGFloat {
        var padding: CGFloat = .spacingLG
        // Add extra padding when footer buttons are shown to prevent content overlap
        if store.isSettingsContext {
            // Footer buttons height (~50px) + vertical padding (16px * 2) + extra spacing
            padding += 90
        }
        // When keyboard is visible, add padding so content can scroll above keyboard
        // The footer buttons will stay above keyboard automatically
        if keyboardHeight > 0 {
            padding += keyboardHeight
        }
        return padding
    }
    
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
                            focusedField: $focusedField,
                            onCommit: {
                                store.networkForm.touchAndValidatePassword()
                                hideKeyboard()
                            },
                            onEditingChanged: { isFocused in
                                if !isFocused {
                                    store.networkForm.touchAndValidatePassword()
                                }
                            }
                        )
                        
                        CustomToggleView(isOn: $store.networkForm.networkHasNoPassword, text: lang.noPasswordToggle)
                            .padding(.top, 0)
                    }
                    .padding(.top, .spacingLG)
                    .padding(.bottom, scrollViewBottomPadding)
                    .navigationBarBackButtonHidden(true)
                }
            }
            .background(theme.backgroundSecondary)
            .scrollDismissesKeyboard(.interactively)
            
            // Show Back and Connect buttons only when reconfiguring WiFi from Settings screen
            // Do not show during initial scale setup flow
            if store.isSettingsContext  {
                settingsFooterButtons
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingSM)
                    .background(theme.backgroundSecondary)
            }
        }
        .keyboardObserver(keyboardHeight: $keyboardHeight)
    }
    
    private var settingsFooterButtons: some View {
        HStack {
            ButtonView(
                text: commonLang.back,
                type: .inlineTextPrimary,
                size: .small,
                isDisabled: false,
                action: {
                    withAnimation {
                        hideKeyboard()
                        store.handleBackButtonClick()
                    }
                }
            )
            
            Spacer()
            
            ButtonView(
                text: lang.connectButtonTitle,
                type: .filledPrimary,
                size: .small,
                isDisabled: !store.isFormValid,
                action: {
                    withAnimation {
                        hideKeyboard()
                        store.handleNextButtonClick()
                    }
                }
            )
        }
    }
    
}

#Preview {
    let store = BtWifiScaleSetupStore()
    store.configure(with: "0412", isWifiSetupOnly: true)
    return WifiPasswordEntryView(
        wifiDetail: WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"),
        isScaleSetup: true
    )
    .environmentObject(store)
}
