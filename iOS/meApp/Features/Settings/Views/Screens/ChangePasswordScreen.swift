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
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation

    @State private var focusedField: FocusField? = nil

    private let labels = InputFieldLabels.self
    private let screenLang = ChangePasswordStrings.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView(
                title: screenLang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        // Disable when no changes or invalid.
                        isDisabled: (!settingsStore.changePasswordForm.isDirty || (settingsStore.changePasswordForm.isDirty && settingsStore.changePasswordForm.isInvalid))
                    ) {
                        hideKeyboard()
                        settingsStore.savePassword(router: router)
                    } },
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
                        focusedField = .confirmNewPassword
                    }

                    // Confirm new password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.confirmPassword,
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.confirmNewPassword),
                            focusField: .confirmNewPassword
                        ),
                        value: $settingsStore.changePasswordForm.confirmNewPassword.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                    }
                }               
                .padding(.top, .spacingXS)
                ButtonView(text: screenLang.forgotPassword, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    settingsStore.showForgotPasswordAlert()
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .padding(.horizontal, .spacingSM)
            .navigationBarHidden(true)
            .padding(.vertical, .spacingLG)
            .padding(.bottom, .spacingXL)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            registerDeactivation {
                // If the form is pristine we can simply pop the screen and allow tab switch.
                if !settingsStore.changePasswordForm.isDirty {
                    router.navigateBack()
                    return true
                }
                // Otherwise ask the user to confirm discarding changes.
                let confirmed = await settingsStore.confirmDiscardPasswordChanges()
                if confirmed {
                    router.navigateBack()
                }
                return confirmed
            }
        }
        .onDisappear {
            registerDeactivation { true }
        }
    }
}

#Preview {
    ChangePasswordScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
} 
