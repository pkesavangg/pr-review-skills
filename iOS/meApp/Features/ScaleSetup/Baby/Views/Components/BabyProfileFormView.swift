//
//  BabyProfileFormView.swift
//  meApp
//

import SwiftUI

/// "Complete Baby Profile" — form for name, birthday, sex, birth length/weight.
struct BabyProfileFormView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    @State private var showSexPicker = false
    private let lang = BabyScaleSetupStrings.BabyProfile.self

    private let sexOptions = [
        BabyScaleSetupStrings.BabyProfile.male,
        BabyScaleSetupStrings.BabyProfile.female
    ]

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    private var birthWeightHasValue: Bool {
        !store.babyProfileForm.birthWeightLbs.value.isEmpty
            || !store.babyProfileForm.birthWeightOz.value.isEmpty
    }

    /// Display text for the biological sex picker.
    private var sexDisplayText: String {
        let val = store.babyProfileForm.biologicalSex.value
        return val.isEmpty ? lang.biologicalSexLabel : val
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingMD) {
                // Header
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)

                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }

                // Name
                AppInputField(
                    config: TextInputConfig(
                        label: lang.namePlaceholder,
                        placeholder: lang.namePlaceholder,
                        inputType: .text,
                        errorMessage: store.babyProfileForm.getNameError(),
                        focusField: .firstName
                    ),
                    value: $store.babyProfileForm.name.value,
                    focusedField: focusBinding
                )

                // Birthday
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.birthdayLabel)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textBody)

                    DatePicker(
                        "",
                        selection: $store.babyProfileForm.birthday.value,
                        in: ...Date(),
                        displayedComponents: .date
                    )
                    .datePickerStyle(.compact)
                    .labelsHidden()
                }

                // Biological Sex
                Button {
                    showSexPicker = true
                } label: {
                    HStack {
                        Text(sexDisplayText)
                            .fontOpenSans(.subHeading1)
                            .foregroundColor(
                                store.babyProfileForm.biologicalSex.value.isEmpty
                                    ? theme.textSubheading
                                    : theme.textBody
                            )
                        Spacer()
                        Image(systemName: "chevron.down")
                            .foregroundColor(theme.textSubheading)
                    }
                    .frame(height: 56)
                    .padding(.horizontal, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusSM)
                }
                .buttonStyle(.plain)
                .sheet(isPresented: $showSexPicker) {
                    sexPickerSheet
                }

                // Birth Length
                VStack(alignment: .leading, spacing: 4) {
                    ZStack(alignment: .leading) {
                        if !store.babyProfileForm.birthLengthInches.value.isEmpty {
                            Text(lang.birthLengthLabel)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .offset(y: -14)
                        }

                        HStack {
                            TextField(
                                lang.birthLengthLabel,
                                text: $store.babyProfileForm.birthLengthInches.value
                            )
                            .keyboardType(.decimalPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .inches)
                            .padding(.top, store.babyProfileForm.birthLengthInches.value.isEmpty ? 0 : 8)
                            .onChange(of: store.babyProfileForm.birthLengthInches.value) { _, newValue in
                                store.babyProfileForm.birthLengthInches.value = formatDecimalInput(
                                    newValue,
                                    maxDigits: 3
                                )
                            }

                            Text("in")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)
                        }
                    }
                    .frame(height: 56)
                    .padding(.horizontal, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusSM)

                    if let error = store.babyProfileForm.getBirthLengthError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                    }
                }

                // Birth Weight
                VStack(alignment: .leading, spacing: 4) {
                    ZStack(alignment: .leading) {
                        if !store.babyProfileForm.birthWeightLbs.value.isEmpty
                            || !store.babyProfileForm.birthWeightOz.value.isEmpty {
                            Text(lang.birthWeightLabel)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .offset(y: -14)
                        }

                        HStack(spacing: .spacingSM) {
                            TextField(
                                birthWeightHasValue ? "" : lang.birthWeightLabel,
                                text: $store.babyProfileForm.birthWeightLbs.value
                            )
                            .keyboardType(.numberPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .pounds)
                            .padding(.top, birthWeightHasValue ? 8 : 0)
                            .onChange(of: store.babyProfileForm.birthWeightLbs.value) { _, newValue in
                                let filtered = newValue.filter { $0.isNumber }
                                store.babyProfileForm.birthWeightLbs.value = String(filtered.prefix(3))
                            }

                            Text("lb")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)

                            TextField(
                                "",
                                text: $store.babyProfileForm.birthWeightOz.value
                            )
                            .keyboardType(.decimalPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .ounces)
                            .padding(.top, birthWeightHasValue ? 8 : 0)
                            .onChange(of: store.babyProfileForm.birthWeightOz.value) { _, newValue in
                                store.babyProfileForm.birthWeightOz.value = formatDecimalInput(
                                    newValue,
                                    maxDigits: 3
                                )
                            }

                            Text("oz")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)
                        }
                    }
                    .frame(height: 56)
                    .padding(.horizontal, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusSM)

                    if let error = store.babyProfileForm.getBirthWeightError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private var sexPickerSheet: some View {
        VStack(spacing: 0) {
            HStack {
                Text(lang.biologicalSexLabel)
                    .fontOpenSans(.heading5)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                Spacer()
                Button {
                    showSexPicker = false
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(theme.textBody)
                }
            }
            .padding(.spacingSM)
            .padding(.top, .spacingXS)

            ForEach(sexOptions, id: \.self) { option in
                Button {
                    store.babyProfileForm.biologicalSex.value = option
                    showSexPicker = false
                } label: {
                    HStack {
                        Text(option)
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textBody)
                        Spacer()
                        if store.babyProfileForm.biologicalSex.value == option {
                            Image(systemName: "checkmark")
                                .foregroundColor(theme.actionPrimary)
                        }
                    }
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingMD)
                }
            }

            Spacer()
        }
        .background(theme.backgroundSecondary)
        .presentationDetents([.fraction(0.3)])
        .presentationDragIndicator(.visible)
    }

    private func formatDecimalInput(_ value: String, maxDigits: Int) -> String {
        let digits = String(value.filter { $0.isNumber }.prefix(maxDigits))
        guard digits.count > 1 else { return digits }
        let intPart = String(digits.dropLast())
        let decPart = String(digits.suffix(1))
        return "\(intPart).\(decPart)"
    }
}
