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

    private let capitalizedSexDisplay: (Sex) -> String = { $0.rawValue.capitalized }
    private let identityDisplay: (String) -> String = { $0 }

    var body: some View {
        VStack(spacing: 0) {
            headerView()
            formScrollView()
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .pickerSheet(
            isPresented: $settingsStore.showGenderPicker,
            selectedValues: [settingsStore.editProfileForm.gender.value],
            options: [Sex.allCases],
            displayValue: capitalizedSexDisplay,
            title: SettingsStrings.biologicalSex
        ) { vals in
            if let sex = vals.first {
                settingsStore.updateGenderInForm(sex)
            }
        }
        .pickerSheet(
            isPresented: $settingsStore.showHeightInchesPicker,
            selectedValues: settingsStore.selectedHeightInches,
            options: settingsStore.heightInchesOptions,
            displayValue: identityDisplay,
            pickerType: .heightInches,
            title: SettingsStrings.height
        ) { newValues in
            settingsStore.updateHeightInForm(fromMetric: false, values: newValues)
        }
        .pickerSheet(
            isPresented: $settingsStore.showHeightCmPicker,
            selectedValues: settingsStore.selectedHeightCm,
            options: settingsStore.heightCmOptions,
            displayValue: identityDisplay,
            pickerType: .heightCm,
            title: SettingsStrings.height
        ) { newValues in
            settingsStore.updateHeightInForm(fromMetric: true, values: newValues)
        }
        .onAppear {
            settingsStore.populateEditFormIfNeeded()

            registerDeactivation {
                if !settingsStore.editProfileForm.isDirty {
                    router.navigateBack()
                    return true
                }
                let confirmed = await settingsStore.confirmDiscardProfileChanges()
                if confirmed {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        router.navigateBack()
                        settingsStore.resetEditProfileForm()
                    }
                }
                return confirmed
            }
        }
        .onDisappear {
            registerDeactivation { true }
        }
        .onChange(of: focusedField) { _, _ in
            showDatePicker = false
        }
    }

    // MARK: - Header

    private func headerView() -> some View {
        NavbarHeaderView(
            title: screenLang.title,
            leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
            trailingContent: {
                ButtonView(
                    text: commonLang.save,
                    type: .inlineTextPrimary,
                    size: .small,
                    isDisabled: !settingsStore.editProfileForm.isDirty
                        || (settingsStore.editProfileForm.isDirty && settingsStore.editProfileForm.isInvalid)
                ) {
                    hideKeyboard()
                    settingsStore.saveProfile(router: router)
                }
                .accessibilityIdentifier(AccessibilityID.profileSaveButton) },
            onLeadingTap: { settingsStore.handleEditProfileExit(router: router) },
            onTrailingTap: {},
            canShowBorder: true
        )
    }

    // MARK: - Form

    private func formScrollView() -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 4) {
                nameFields()
                contactFields()
                birthdayField()
                deviceSettingsFields()
            }
            .padding(.vertical, .spacingLG)
            .padding(.bottom, .spacingXL)
        }
        .scrollDismissesKeyboard(.interactively)
        .padding(.horizontal, .spacingSM)
        .navigationBarHidden(true)
    }

    // MARK: - Field Groups

    private func nameFields() -> some View {
        Group {
            AppInputField(
                config: TextInputConfig(
                    label: labels.firstName,
                    inputType: .text,
                    errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.firstName),
                    focusField: .firstName
                ),
                value: $settingsStore.editProfileForm.firstName.value,
                focusedField: $focusedField,
                accessibilityIdentifier: AccessibilityID.firstNameField
            ) {
                focusedField = .lastName
            }

            AppInputField(
                config: TextInputConfig(
                    label: labels.lastName,
                    inputType: .text,
                    errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.lastName),
                    focusField: .lastName
                ),
                value: $settingsStore.editProfileForm.lastName.value,
                focusedField: $focusedField,
                accessibilityIdentifier: AccessibilityID.lastNameField
            ) {
                focusedField = .email
            }
        }
    }

    private func contactFields() -> some View {
        Group {
            AppInputField(
                config: TextInputConfig(
                    label: labels.email,
                    inputType: .email,
                    errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.email),
                    focusField: .email
                ),
                value: $settingsStore.editProfileForm.email.value,
                focusedField: $focusedField,
                accessibilityIdentifier: AccessibilityID.emailField
            ) {
                focusedField = .zipCode
            }

            AppInputField(
                config: TextInputConfig(
                    label: labels.zipCode,
                    inputType: .text,
                    errorMessage: settingsStore.editProfileForm.getError(for: settingsStore.editProfileForm.zipcode),
                    focusField: .zipCode
                ),
                value: $settingsStore.editProfileForm.zipcode.value,
                focusedField: $focusedField,
                accessibilityIdentifier: AccessibilityID.zipcodeField
            ) {
                focusedField = nil
            }
        }
    }

    private func birthdayField() -> some View {
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

    private func deviceSettingsFields() -> some View {
        let settingsLang = SettingsStrings.self
        return Group {
            VStack(alignment: .leading, spacing: .spacingMD) {
                // Biological Sex
                VStack(alignment: .leading, spacing: .spacingXS) {
                    ActionListItemView(config: ActionListItemConfig(
                        title: settingsLang.biologicalSex,
                        value: settingsStore.editBiologicalSexText,
                        chevronType: .upDown) { settingsStore.presentGenderPicker() })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(8)
                        .accessibilityIdentifier(AccessibilityID.settingsRowBiologicalSex)

                    Text(screenLang.biologicalSexNote)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                }

                // Height
                VStack(alignment: .leading, spacing: .spacingXS) {
                    ActionListItemView(config: ActionListItemConfig(
                        title: settingsLang.height,
                        value: settingsStore.editHeightText,
                        chevronType: .upDown) { settingsStore.presentHeightPicker() })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(8)
                        .accessibilityIdentifier(AccessibilityID.settingsRowHeight)

                    Text(screenLang.heightNote)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                }
            }
            .padding(.top, .spacingMD)
        }
    }
}

#Preview {
    EditProfileScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
}
