//
//  ChangePasswordScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 20/06/25.
//

import SwiftUI
import Combine

// MARK: - Change Password Screen
/// A screen that allows users to change their account password.
/// Mimics the design & behaviour of EditProfileScreen (exit alert, save button state).
/// Uses the shared `AppInputField` for the three password fields.
struct ChangePasswordScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var settingsStore: SettingsStore
    @EnvironmentObject var router: Router<SettingsRoute>

    @State private var focusedField: FocusField? = nil

    private let labels = InputFieldLabels.self
    private let screenLang = ChangePasswordStrings.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView(
                title: screenLang.title,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: { EmptyView() },
                onLeadingTap: { settingsStore.handleChangePasswordExit(router: router) },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 4) {
                    // Current password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.currentPassword,
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.currentPassword),
                            focusField: .currentPassword
                        ),
                        value: $settingsStore.changePasswordForm.currentPassword.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .newPassword
                    }

                    // New password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.password, // Reuse existing label "password"
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.newPassword),
                            focusField: .newPassword
                        ),
                        value: $settingsStore.changePasswordForm.newPassword.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .repeatNewPassword
                    }

                    // Confirm new password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.confirmPassword,
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.repeatPassword),
                            focusField: .repeatNewPassword
                        ),
                        value: $settingsStore.changePasswordForm.repeatPassword.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                    }

                    // Save Button
                    HStack {
                        ButtonView(text: commonLang.save,
                                   type: .primary,
                                   size: .regular,
                                   isDisabled: (!settingsStore.changePasswordForm.isDirty || (settingsStore.changePasswordForm.isDirty && settingsStore.changePasswordForm.isInvalid))) {
                            hideKeyboard()
                            settingsStore.savePassword(router: router)
                        }
                        .padding(.top, .spacingXL)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .padding(.vertical, .spacingLG)
                .padding(.bottom, .spacingXL)
            }
            .scrollDismissesKeyboard(.interactively)
            .padding(.horizontal, .spacingSM)
            .navigationBarHidden(true)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}

#Preview {
    ChangePasswordScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
} 