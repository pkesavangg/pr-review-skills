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
                Menu {
                    ForEach(sexOptions, id: \.self) { option in
                        Button(option) {
                            store.babyProfileForm.biologicalSex.value = option
                        }
                    }
                } label: {
                    HStack {
                        Text(sexDisplayText)
                            .fontOpenSans(.body1)
                            .foregroundColor(
                                store.babyProfileForm.biologicalSex.value.isEmpty
                                    ? theme.textBody.opacity(0.5)
                                    : theme.textBody
                            )
                        Spacer()
                        Image(systemName: "chevron.down")
                            .foregroundColor(theme.textBody)
                    }
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusSM)
                }

                // Birth Length
                VStack(alignment: .leading, spacing: 0) {
                    Divider()
                    HStack {
                        Text(lang.birthLengthLabel.capitalized)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                        Spacer()
                        TextField("", text: $store.babyProfileForm.birthLengthInches.value)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .font(.body2)
                            .bold()
                            .foregroundColor(theme.textHeading)
                            .frame(width: 60)
                            .focused($focusedField, equals: .inches)
                            .onChange(of: store.babyProfileForm.birthLengthInches.value) { _, newValue in
                                store.babyProfileForm.birthLengthInches.value = formatDecimalInput(newValue, maxDigits: 3)
                            }
                        Text("in")
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textSubheading)
                    }
                    .padding(.vertical, .spacingSM)
                    Divider()

                    if let error = store.babyProfileForm.getBirthLengthError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                            .padding(.top, 2)
                    }
                }

                // Birth Weight
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text(lang.birthWeightLabel.capitalized)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                        Spacer()
                        TextField("", text: $store.babyProfileForm.birthWeightLbs.value)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .font(.body2)
                            .bold()
                            .foregroundColor(theme.textHeading)
                            .frame(width: 50)
                            .focused($focusedField, equals: .pounds)
                            .onChange(of: store.babyProfileForm.birthWeightLbs.value) { _, newValue in
                                let filtered = newValue.filter { $0.isNumber }
                                store.babyProfileForm.birthWeightLbs.value = String(filtered.prefix(3))
                            }
                        Text("lb")
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textSubheading)
                        TextField("", text: $store.babyProfileForm.birthWeightOz.value)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .font(.body2)
                            .bold()
                            .foregroundColor(theme.textHeading)
                            .frame(width: 50)
                            .focused($focusedField, equals: .ounces)
                            .onChange(of: store.babyProfileForm.birthWeightOz.value) { _, newValue in
                                store.babyProfileForm.birthWeightOz.value = formatDecimalInput(newValue, maxDigits: 3)
                            }
                        Text("oz")
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textSubheading)
                    }
                    .padding(.vertical, .spacingSM)
                    Divider()

                    if let error = store.babyProfileForm.getBirthWeightError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                            .padding(.top, 2)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    /// Auto-formats numeric input with a decimal before the last digit (e.g. "888" → "88.8").
    /// Max total digits (excluding decimal) is `maxDigits`.
    private func formatDecimalInput(_ value: String, maxDigits: Int) -> String {
        let digits = String(value.filter { $0.isNumber }.prefix(maxDigits))
        guard digits.count > 1 else { return digits }
        let intPart = String(digits.dropLast())
        let decPart = String(digits.suffix(1))
        return "\(intPart).\(decPart)"
    }
}
