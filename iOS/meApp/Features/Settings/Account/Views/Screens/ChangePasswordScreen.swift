//
//  ChangePasswordScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 20/06/25.
//

import Combine
import SwiftUI

// MARK: - Change Password Screen
/// A screen that allows users to change their account password.
/// Mimics the design & behaviour of EditProfileScreen (exit alert, save button state).
/// Uses the shared `AppInputField` for the three password fields.
struct ChangePasswordScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var settingsStore: SettingsStore
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation

    @State private var focusedField: FocusField?

    private let labels = InputFieldLabels.self
    private let screenLang = ChangePasswordStrings.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView(
                title: screenLang.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        // Disable when no changes or invalid.
                        isDisabled: !settingsStore.changePasswordForm.isDirty
                            || (settingsStore.changePasswordForm.isDirty && settingsStore.changePasswordForm.isInvalid)
                    ) {
                        hideKeyboard()
                        settingsStore.savePassword(router: router)
                    }
                    .appAccessibility(id: AccessibilityID.changePasswordSaveButton) },
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
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.currentPasswordField,
                        onCommit: {
                            settingsStore.touchAndValidate(field: .currentPassword)
                            focusedField = .newPassword
                        },
                        onEditingChanged: { isEditing in
                            settingsStore.handleEditingChanged(isEditing, field: .currentPassword)
                        }
                    )
                    .onChange(of: focusedField) { oldValue, newValue in
                        if oldValue == .currentPassword && newValue != .currentPassword {
                            settingsStore.touchAndValidate(field: .currentPassword)
                        }
                    }

                    // New password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.createNewPassword,
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.newPassword),
                            focusField: .newPassword
                        ),
                        value: $settingsStore.changePasswordForm.newPassword.value,
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.newPasswordField,
                        onCommit: {
                            settingsStore.touchAndValidate(field: .newPassword)
                            focusedField = .confirmNewPassword
                        },
                        onEditingChanged: { isEditing in
                            settingsStore.handleEditingChanged(isEditing, field: .newPassword)
                        }
                    )
                    .onChange(of: focusedField) { oldValue, newValue in
                        if oldValue == .newPassword && newValue != .newPassword {
                            settingsStore.touchAndValidate(field: .newPassword)
                        }
                    }

                    // Confirm new password
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.confirmNewPassword,
                            inputType: .password,
                            errorMessage: settingsStore.changePasswordForm.getError(for: settingsStore.changePasswordForm.confirmNewPassword),
                            focusField: .confirmNewPassword
                        ),
                        value: $settingsStore.changePasswordForm.confirmNewPassword.value,
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.confirmPasswordField,
                        onCommit: {
                            settingsStore.touchAndValidate(field: .confirmNewPassword)
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            settingsStore.handleEditingChanged(isEditing, field: .confirmNewPassword)
                        }
                    )
                    .onChange(of: focusedField) { oldValue, newValue in
                        if oldValue == .confirmNewPassword && newValue != .confirmNewPassword {
                            settingsStore.touchAndValidate(field: .confirmNewPassword)
                        }
                    }
                }               
                .padding(.top, .spacingXS)
                ButtonView(text: screenLang.forgotPassword, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    settingsStore.showForgotPasswordAlert()
                }
                .appAccessibility(id: AccessibilityID.changePasswordForgotPasswordButton)
            }
            .scrollDismissesKeyboard(.interactively)
            .padding(.horizontal, .spacingSM)
            .navigationBarHidden(true)
            .padding(.vertical, .spacingLG)
            .padding(.bottom, .spacingXL)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .screenAccessibilityRoot(AccessibilityID.changePasswordScreenRoot)
        .onTapGesture {
            focusedField = nil
            hideKeyboard()
        }
        .onAppear {
            settingsStore.resetChangePasswordForm()
            registerDeactivation {
                // If the form is pristine we can simply pop the screen and allow tab switch.
                if !settingsStore.changePasswordForm.isDirty {
                    router.navigateBack()
                    return true
                }
                // Otherwise ask the user to confirm discarding changes.
                let confirmed = await settingsStore.confirmDiscardPasswordChanges()
                if confirmed {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        router.navigateBack()
                        settingsStore.resetChangePasswordForm()
                    }
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
