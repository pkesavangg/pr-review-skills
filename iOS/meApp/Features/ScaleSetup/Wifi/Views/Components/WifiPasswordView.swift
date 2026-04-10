//
//  WifiPasswordView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//
import SwiftUI

struct WifiPasswordView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var store: WifiScaleSetupStore
    @State private var focusedField: FocusField?
    var showWifiConnectionDetails: Bool = true
    var onClickNetworkName: (() -> Void)?
    private let labels = InputFieldLabels.self
    private let lang = WifiScaleSetupStrings.WifiPasswordViewStrings.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textBody)
                        Text(showWifiConnectionDetails ? lang.description : lang.simpleDescription)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    .padding(.bottom, .spacingLG)
                    
                    VStack {
                        AppInputField(
                            config: TextInputConfig(
                                label: labels.networkName,
                                placeholder: store.permissionsSkipped ? "" : nil,
                                inputType: .text,
                                submitLabel: .next,
                                errorMessage: store.networkForm.getError(for: store.networkForm.ssid),
                                focusField: .networkName
                            ),
                            value: $store.networkForm.ssid.value,
                            focusedField: $focusedField,
                            onCommit: {
                                store.networkForm.touchAndValidateSSID()
                                focusedField = .password
                            },
                            onEditingChanged: { isFocused in
                                if !isFocused {
                                    store.networkForm.touchAndValidateSSID()
                                }
                            }
                        )
                        .onChange(of: focusedField) { oldValue, newValue in
                            if oldValue == .networkName && newValue != .networkName {
                                store.networkForm.touchAndValidateSSID()
                            }
                        }
                        // Clear SSID only if permissions were skipped and are still disabled
                        .onAppear {
                            if store.permissionsSkipped && !store.arePermissionsEnabled() {
                                store.networkForm.clearSSIDAndMarkPristine()
                            }
                        }
                        
                        AppInputField(
                            config: TextInputConfig(
                                label: labels.password,
                                inputType: .password,
                                submitLabel: .done,
                                errorMessage: store.networkForm.getError(for: store.networkForm.password),
                                isDisabled: store.networkForm.networkHasNoPassword,
                                focusField: .password,
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
                        .onChange(of: focusedField) { oldValue, newValue in
                            if oldValue == .password && newValue != .password {
                                store.networkForm.touchAndValidatePassword()
                            }
                        }
                        
                        CustomToggleView(isOn: $store.networkForm.networkHasNoPassword, text: lang.networkHasNoPassword)
                            .padding(.top, 0)
                    }
                    
                    NoteBox {
                        Text("**NOTE:** \(lang.note)")
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textBody)
                    }
                    .padding(.top, .spacingMD)
                }
                .padding(.top, .spacingLG)
                .navigationBarBackButtonHidden(true)
            }
        }
        .background(theme.backgroundSecondary)
        .scrollDismissesKeyboard(.interactively)
    }
}

// MARK: - Local test wrapper
struct TestWifiPasswordEntryView: View {
    @StateObject private var store = WifiScaleSetupStore()
    var body: some View {
        WifiPasswordView()
            .padding(.horizontal, .spacingSM)
            .environmentObject(store)
            .onAppear {
                store.configure(with: "0385")
            }
    }
}
