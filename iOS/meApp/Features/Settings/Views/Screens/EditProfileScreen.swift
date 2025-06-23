//
//  EditProfileScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//
import SwiftUI
import Combine

// MARK: - Edit Profile Screen
/// A screen that allows users to edit basic profile details (first/last name, email, ZIP code, birthday).
/// All text constants come from dedicated `Strings` objects to satisfy localisation rules.
struct EditProfileScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var settingsStore: SettingsStore
    @EnvironmentObject var router: Router<SettingsRoute>

    @State private var focusedField: FocusField? = nil
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
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
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
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(labels.date.uppercased())
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textBody)

                        DateLabelView(date: settingsStore.editProfileForm.birthday.value) {
                            withAnimation { showDatePicker.toggle() }
                        }

                        DatePickerView(isPresented: $showDatePicker,
                                       date: $settingsStore.editProfileForm.birthday.value,
                                       endDate: maxDate)
                    }
                    HStack {
                        // Save Button
                        ButtonView(text: commonLang.save,
                                   type: .filledPrimary,
                                   size: .large,
                                   isDisabled: (!settingsStore.editProfileForm.isDirty || (settingsStore.editProfileForm.isDirty && settingsStore.editProfileForm.isInvalid))) {
                            hideKeyboard()
                            settingsStore.saveProfile(router: router)
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
        .onAppear { settingsStore.populateEditFormIfNeeded() }
    }
}

#Preview {
    EditProfileScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
}
