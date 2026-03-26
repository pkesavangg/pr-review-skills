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

    /// When `true`, the title and subtitle header is hidden (e.g. Settings → Add Baby).
    var hideHeader: Bool = false

    /// The currently selected `Sex` value derived from the form string, defaulting to `.male`.
    private var selectedSex: Sex {
        Sex(rawInput: store.babyProfileForm.biologicalSex.value) ?? .male
    }

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

    private var birthWeightIsActive: Bool {
        birthWeightHasValue || focusedField == .pounds || focusedField == .ounces
    }

    /// Display text for the biological sex picker.
    private var sexDisplayText: String {
        let val = store.babyProfileForm.biologicalSex.value
        return val.isEmpty ? lang.biologicalSexLabel : val
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                // Header (scale setup only)
                if !hideHeader {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)

                        Text(lang.subtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
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
                .padding(.top, -.spacingSM)

                // Biological Sex
                VStack(alignment: .leading, spacing: 4) {
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
                            Image(systemName: "chevron.up.chevron.down")
                                .foregroundColor(theme.textSubheading)
                        }
                        .frame(height: 56)
                        .padding(.horizontal, .spacingSM)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.radiusSM)
                    }
                    .buttonStyle(.plain)

                    if let error = store.babyProfileForm.getBiologicalSexError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                    }
                }

                // Birth Length
                VStack(alignment: .leading, spacing: 4) {
                    ZStack(alignment: .leading) {
                        if !store.babyProfileForm.birthLengthInches.value.isEmpty || focusedField == .inches {
                            Text(lang.birthLengthLabel)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .offset(y: -14)
                        }

                        HStack {
                            TextField(
                                focusedField == .inches ? "" : lang.birthLengthLabel,
                                text: $store.babyProfileForm.birthLengthInches.value
                            )
                            .keyboardType(.decimalPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .inches)
                            .padding(.top, (!store.babyProfileForm.birthLengthInches.value.isEmpty || focusedField == .inches) ? 8 : 0)
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
                        if birthWeightIsActive {
                            Text(lang.birthWeightLabel)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .offset(y: -14)
                        }

                        HStack(spacing: .spacingSM) {
                            TextField(
                                birthWeightIsActive ? "" : lang.birthWeightLabel,
                                text: $store.babyProfileForm.birthWeightLbs.value
                            )
                            .keyboardType(.numberPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .pounds)
                            .padding(.top, birthWeightIsActive ? 8 : 0)
                            .onChange(of: store.babyProfileForm.birthWeightLbs.value) { _, newValue in
                                let filtered = newValue.filter { $0.isNumber }
                                store.babyProfileForm.birthWeightLbs.value = String(filtered.prefix(3))
                            }

                            Text("lb")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)
                                .fixedSize()
                                .padding(.top, birthWeightIsActive ? 8 : 0)

                            TextField(
                                "",
                                text: $store.babyProfileForm.birthWeightOz.value
                            )
                            .keyboardType(.decimalPad)
                            .font(.subHeading1)
                            .foregroundColor(theme.textBody)
                            .focused($focusedField, equals: .ounces)
                            .padding(.top, birthWeightIsActive ? 8 : 0)
                            .onChange(of: store.babyProfileForm.birthWeightOz.value) { _, newValue in
                                store.babyProfileForm.birthWeightOz.value = formatDecimalInput(
                                    newValue,
                                    maxDigits: 3
                                )
                            }

                            Text("oz")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)
                                .fixedSize()
                                .padding(.top, birthWeightIsActive ? 8 : 0)
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
            .padding(.top, .spacingLG)
        }
        .scrollDismissesKeyboard(.interactively)
        .pickerSheet(
            isPresented: $showSexPicker,
            selectedValues: [selectedSex],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: lang.biologicalSexLabel,
            onUpdate: { vals in
                if let sex = vals.first {
                    store.babyProfileForm.biologicalSex.value = sex.rawValue.capitalized
                    store.babyProfileForm.biologicalSex.markAsTouched()
                    store.babyProfileForm.biologicalSex.validate()
                }
            }
        )
        .onChange(of: focusedField) { oldValue, _ in
            switch oldValue {
            case .firstName:
                store.babyProfileForm.name.markAsTouched()
                store.babyProfileForm.name.validate()
            case .inches:
                store.babyProfileForm.birthLengthInches.markAsTouched()
                store.babyProfileForm.birthLengthInches.validate()
            case .pounds:
                store.babyProfileForm.birthWeightLbs.markAsTouched()
                store.babyProfileForm.birthWeightLbs.validate()
            case .ounces:
                store.babyProfileForm.birthWeightOz.markAsTouched()
                store.babyProfileForm.birthWeightOz.validate()
            default: break
            }
        }
    }

    private func formatDecimalInput(_ value: String, maxDigits: Int) -> String {
        let digits = String(value.filter { $0.isNumber }.prefix(maxDigits))
        guard digits.count > 1 else { return digits }
        let intPart = String(digits.dropLast())
        let decPart = String(digits.suffix(1))
        return "\(intPart).\(decPart)"
    }
}
