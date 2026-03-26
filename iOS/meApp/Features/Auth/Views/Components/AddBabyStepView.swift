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

                    // Biological Sex dropdown
                    BabySexPickerField(signupStore: signupStore)

                    // Birth Length
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthLength,
                            inputType: .text,
                            errorMessage: nil,
                            focusField: .babyBirthLength
                        ),
                        value: $signupStore.signupForm.babyBirthLength.value,
                        focusedField: $focusedField,
                        onCommit: {
                            focusedField = .babyBirthWeight
                        },
                        onEditingChanged: { _ in }
                    )

                    // Birth Weight
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthWeight,
                            inputType: .text,
                            errorMessage: nil,
                            focusField: .babyBirthWeight
                        ),
                        value: $signupStore.signupForm.babyBirthWeight.value,
                        focusedField: $focusedField,
                        onCommit: {
                            focusedField = nil
                        },
                        onEditingChanged: { _ in }
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
    }
}

// MARK: - BabySexPickerField

private struct BabySexPickerField: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    let labels = InputFieldLabels.self

    var body: some View {
        Menu {
            ForEach(Sex.allCases, id: \.self) { sex in
                Button(sex.rawValue.capitalized) {
                    signupStore.signupForm.babySex.value = sex.rawValue
                }
            }
        } label: {
            HStack {
                Text(displayText)
                    .fontOpenSans(.body3)
                    .foregroundColor(
                        signupStore.signupForm.babySex.value.isEmpty
                            ? theme.textSubheading
                            : theme.textHeading
                    )
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundColor(theme.textSubheading)
                    .font(.system(size: 14))
            }
            .padding(.horizontal, .spacingSM)
            .frame(height: 56)
            .background(theme.backgroundPrimary)
            .cornerRadius(.spacingXS)
        }
    }

    private var displayText: String {
        let value = signupStore.signupForm.babySex.value
        return value.isEmpty ? labels.biologicalSex : value.capitalized
    }
}

#Preview {
    AddBabyStepView(signupStore: SignupStore())
        .padding()
}
