//
//  WifiCredentialsView.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

struct WifiCredentialsView: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var store = ScaleStore()
    @FocusState private var focusedField: FocusField?
    @State private var networkHasNoPassword: Bool = false
    @State private var showLoader: Bool = false
    let lang = WifiScreenStrings.self
    let commonLang = CommonStrings.self
    var labels = InputFieldLabels.self
    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }
    let wifiName: String
    
    var body: some View {
        ZStack {
            if showLoader {
                ConnectingWifiLoaderView(store: store)
                    .onAppear {
                        store.connectToWifiNetwork(wifiName: wifiName)
                    }
                    .onChange(of: store.wifiConnectionState) { oldState, newState in
                    }
            } else {
                mainForm
            }
        }
        .onTapGesture {
            hideKeyboard()
        }
        .ignoresSafeArea(.keyboard)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
    
    var mainForm: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            VStack(alignment: .leading){
                VStack(alignment: .leading, spacing: .spacingXS){
                    Text(lang.enterPasswordTitle)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textBody)
                    
                    (
                        Text(lang.enterPasswordSubtitlePrefix)
                        + Text(wifiName).fontWeight(.bold)
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
                        inputType: store.showPassword ? .text : .password,
                        submitLabel: .done,
                        errorMessage: store.passwordError
                    ),
                    value: $store.wifiPasswordValidationForm.password.value,
                    focusedField: focusBinding
                ) {
                    store.setPasswordTouched()
                    focusedField = nil
                    if store.isFormValid && !networkHasNoPassword {
                        showLoader = true
                    }
                }
                .disabled(networkHasNoPassword)
                .opacity(networkHasNoPassword ? 0.5 : 1.0)
                
                CustomToggleView(isOn: $networkHasNoPassword, text: lang.noPasswordToggle)
                    .padding(.top, 0)
                
                Spacer()
                
                HStack{
                    ButtonView(text: commonLang.back, type: .textPrimary, size: .small, isDisabled: false, action: {
                        store.handleWifiCredentialsExit {
                            router.navigateBack()
                        }
                    })
                    Spacer()
                    ButtonView(
                        text: lang.connectButtonTitle,
                        type: .filledPrimary,
                        size: .small,
                        isDisabled: !networkHasNoPassword && !store.isFormValid,
                        action: {
                            if store.isFormValid && !networkHasNoPassword {
                                showLoader = true
                            }
                        }
                    )
                }
                .padding(.bottom, .spacingLG)
            }
            .padding(.top, .spacingLG)
            .padding(.horizontal, .spacingSM)
        }
        .onTapGesture {
            focusedField = nil
        }
    }
}

#Preview{
    WifiCredentialsView( wifiName: "greaterGoods")
}
