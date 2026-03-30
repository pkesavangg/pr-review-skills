//
//  AddBabyStepView.swift
//  meApp
//

import SwiftUI

struct AddBabyStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    @State var focusedField: FocusField?

    let lang = SignupStrings.AddBabyStep.self
    let labels = InputFieldLabels.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                // Header
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }

                // Form fields
                VStack(alignment: .leading, spacing: .spacingMD) {
                    // Baby Name
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyName,
                            inputType: .text,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.babyName),
                            focusField: .babyName
                        ),
                        value: $signupStore.signupForm.babyName.value,
                        focusedField: $focusedField,
                        onCommit: {
                            signupStore.touchAndValidate(field: .babyName)
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            signupStore.handleEditingChanged(isEditing, field: .babyName)
                        }
                    )

                    // Baby's Birthday
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        Text(lang.birthdayLabel)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)

                        DateLabelView(
                            date: signupStore.signupForm.babyBirthday.value,
                            isSelected: signupStore.showBabyDatePicker
                        ) {
                            withAnimation { signupStore.showBabyDatePicker.toggle() }
                        }

                        DatePickerView(
                            isPresented: $signupStore.showBabyDatePicker,
                            date: $signupStore.signupForm.babyBirthday.value,
                            endDate: Date()
                        )
                    }

                    // Biological Sex
                    ActionListItemView(config: ActionListItemConfig(
                        title: labels.biologicalSex,
                        value: babySexDisplayText,
                        chevronType: .upDown) { signupStore.showBabySexPicker = true })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.spacingXS)

                    // Birth Length
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthLength,
                            inputType: .number,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.babyBirthLength),
                            focusField: .babyBirthLength
                        ),
                        value: $signupStore.signupForm.babyBirthLength.value,
                        focusedField: $focusedField,
                        onCommit: {
                            signupStore.touchAndValidate(field: .babyBirthLength)
                            focusedField = .babyBirthWeight
                        },
                        onEditingChanged: { isEditing in
                            signupStore.handleEditingChanged(isEditing, field: .babyBirthLength)
                        }
                    )

                    // Birth Weight
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthWeight,
                            inputType: .number,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.babyBirthWeight),
                            focusField: .babyBirthWeight
                        ),
                        value: $signupStore.signupForm.babyBirthWeight.value,
                        focusedField: $focusedField,
                        onCommit: {
                            signupStore.touchAndValidate(field: .babyBirthWeight)
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            signupStore.handleEditingChanged(isEditing, field: .babyBirthWeight)
                        }
                    )
                }
                .padding(.top, .spacingLG)

                Spacer()
            }
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively)
        .onTapGesture {
            hideKeyboard()
        }
        .onChange(of: signupStore.currentStep) { _, _ in
            signupStore.showBabyDatePicker = false
        }
        .pickerSheet(
            isPresented: $signupStore.showBabySexPicker,
            selectedValues: [selectedBabySex],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: labels.biologicalSex,
            onUpdate: { vals in // swiftlint:disable:this trailing_closure
                if let sex = vals.first {
                    signupStore.signupForm.babySex.value = sex.rawValue
                }
            }
        )
    }

    private var babySexDisplayText: String {
        let value = signupStore.signupForm.babySex.value
        return value.isEmpty ? "" : value.capitalized
    }

    private var selectedBabySex: Sex {
        Sex(rawValue: signupStore.signupForm.babySex.value) ?? .male
    }
}

#Preview {
    AddBabyStepView(signupStore: SignupStore())
        .padding()
}
