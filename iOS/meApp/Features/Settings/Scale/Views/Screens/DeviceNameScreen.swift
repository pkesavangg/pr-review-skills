//
//  DeviceNameScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct DeviceNameScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    let scale: Device
    let lang = DeviceSettingsStrings.self
    let commonLang = CommonStrings.self

    @State private var editedName: String = ""
    @State private var focusedField: FocusField?
    @StateObject private var scaleNameForm = DeviceNameForm()
    @StateObject private var viewModel: DeviceNameViewModel

    init(scale: Device) {
        self.scale = scale
        _viewModel = StateObject(wrappedValue: DeviceNameViewModel(scale: scale))
    }

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.scaleName,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: commonLang.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !scaleNameForm.isValid
                            || editedName.trimmingCharacters(in: .whitespacesAndNewlines)
                                == (scale.nickname ?? scale.deviceName ?? "")
                    ) {
                            Task {
                                let trimmedName = editedName.trimmingCharacters(in: .whitespacesAndNewlines)
                                await viewModel.saveDeviceName(trimmedName) {
                                    router.navigateBack()
                                }
                            }
                        })
                },
                onLeadingTap: {
                    Task {
                        let allow = await viewModel.allowExit(isFormDirty: scaleNameForm.isDirty, editedName: editedName)
                        if allow { router.navigateBack() }
                    }
                },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: .spacingMD) {
                    AppInputField(
                        config: TextInputConfig(
                            label: lang.scaleName,
                            placeholder: lang.scaleName,
                            inputType: .text,
                            errorMessage: scaleNameForm.getError(for: .scaleName),
                            focusField: .scaleName
                        ),
                        value: $editedName,
                        focusedField: $focusedField
                    ) {
                        // Optional: handle commit
                    }
                    .onChange(of: editedName) {
                        scaleNameForm.setDeviceName(editedName)
                    }
                    .padding(.horizontal, .spacingSM)
                    .padding(.top, .spacingMD)

                    Spacer()
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationBarHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onTapGesture {
            focusedField = nil
            hideKeyboard()
        }
        .onAppear {
            editedName = scale.nickname ?? scale.deviceName ?? ""
            scaleNameForm.setDeviceName(editedName)
            scaleNameForm.scaleName.markAsPristine()
            scaleNameForm.validate()
            registerDeactivation {
                await viewModel.allowExit(isFormDirty: scaleNameForm.isDirty, editedName: editedName)
            }
        }
        .onDisappear {
            // Clears the tab-level deactivation gate to prevent stale dirty-form alerts on subsequent tab switches.
            registerDeactivation { true }
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
    DeviceNameScreen(scale: mockDevice)
}
