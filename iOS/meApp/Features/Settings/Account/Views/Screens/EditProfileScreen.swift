import Combine
//
//  EditProfileScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//
import SwiftUI

// MARK: - Edit Profile Screen
/// A screen that allows users to edit basic profile details (first/last name, email, ZIP code, birthday).
/// All text constants come from dedicated `Strings` objects to satisfy localisation rules.
struct EditProfileScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var settingsStore: SettingsStore
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    
    @State private var focusedField: FocusField?
    @State private var showDatePicker = false
    
    private let labels = InputFieldLabels.self
    private let commonLang = CommonStrings.self
    private let screenLang = EditProfileStrings.self
    
    private let maxDate = DateTimeTools.minAllowedBirthdayDate()
    
    var body: some View {
        VStack(spacing: 0) {
            // MARK: Header
            NavbarHeaderView(
                title: screenLang.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        // Disable when no changes or invalid.
// swiftlint:disable:next line_length
                        isDisabled: (!settingsStore.editProfileForm.isDirty || (settingsStore.editProfileForm.isDirty && settingsStore.editProfileForm.isInvalid)),
                    ) {
                        hideKeyboard()
                        settingsStore.saveProfile(router: router)
                    } },
                onLeadingTap: { settingsStore.handleEditProfileExit(router: router) },
                onTrailingTap: {},
                canShowBorder: true
            )
            // MARK: Form
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 4) {
                    // First Name
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.firstName,
                            inputType: .text,
                            errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.firstName),
                            focusField: .firstName
                        ),
                        value: $settingsStore.editProfileForm.firstName.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .lastName
                    }
                    
                    // Last Name
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.lastName,
                            inputType: .text,
                            errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.lastName),
                            focusField: .lastName
                        ),
                        value: $settingsStore.editProfileForm.lastName.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .email
                    }
                    
                    // Email
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.email,
                            inputType: .email,
                            errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.email),
                            focusField: .email
                        ),
                        value: $settingsStore.editProfileForm.email.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .zipCode
                    }
                    
                    // Zip Code
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.zipCode,
                            inputType: .text,
                            errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.zipcode),
                            focusField: .zipCode
                        ),
                        value: $settingsStore.editProfileForm.zipcode.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                    }
                    
                    // Birthday date selector
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        Text(labels.birthday)
                            .fontOpenSans(.subHeading1)
                            .foregroundColor(theme.textSubheading)
                        
                        DateLabelView(
                            date: settingsStore.editProfileForm.birthday.value,
                            isSelected: showDatePicker
                        ) {
                            withAnimation { showDatePicker.toggle() }
                        }
                        .padding(.leading, 2)
                        
                        DatePickerView(isPresented: $showDatePicker,
                                       date: $settingsStore.editProfileForm.birthday.value,
                                       endDate: maxDate)
                    }
                }
                .padding(.vertical, .spacingLG)
                .padding(.bottom, .spacingXL)
            }
            .scrollDismissesKeyboard(.interactively)
            .padding(.horizontal, .spacingSM)
            .navigationBarHidden(true)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            settingsStore.populateEditFormIfNeeded()

            registerDeactivation {
                // If the form has no unsaved changes, allow immediate tab switch.
                if !settingsStore.editProfileForm.isDirty {
                    router.navigateBack()
                    return true
                }

                // Otherwise ask for confirmation.
                let confirmed = await settingsStore.confirmDiscardProfileChanges()
                if confirmed {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        router.navigateBack()
                        settingsStore.resetEditProfileForm()
                    }
                }
                return confirmed
            }
        }
        .onDisappear {
            // Remove deactivation handler when leaving screen.
            registerDeactivation { true }
        }
        .onChange(of: focusedField) { _, _ in
            // Close calendar when focus changes to other fields
            showDatePicker = false
        }
    }
}

#Preview {
    EditProfileScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
}
