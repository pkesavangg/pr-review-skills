//
//  BirthWeightInputField.swift
//  meApp
//

import SwiftUI

/// A compound input field that renders lb and oz text inputs inside a single rounded box
/// with a shared floating label ("Birth Weight") and combined error message.
struct BirthWeightInputField: View {
    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.BabyProfile.self

    // External bindings
    @Binding var lbsValue: String
    @Binding var ozValue: String
    @Binding var focusedField: FocusField?

    // Configuration
    var label: String
    var errorMessage: String?

    // Internal display values (formatted by MetricFieldFormatter)
    @State private var lbsDisplayValue: String = ""
    @State private var ozDisplayValue: String = ""

    // Formatters
    @StateObject private var lbsFormatter = MetricFieldFormatter(
        config: TextInputConfig(label: "", inputType: .metric, maxLength: 3, allowWholeNumbers: true)
    )
    @StateObject private var ozFormatter = MetricFieldFormatter(
        config: TextInputConfig(label: "", inputType: .metric, maxLength: 3, clearZeroValue: true)
    )

    // Focus states
    @FocusState private var lbsFieldFocused: Bool
    @FocusState private var ozFieldFocused: Bool

    private var isFloatingLabelActive: Bool {
        lbsFieldFocused || ozFieldFocused || !lbsValue.isEmpty || !ozValue.isEmpty
    }

    private var floatingLabelColor: Color {
        errorMessage != nil ? theme.textError : theme.textSubheading
    }

    private var accentColor: Color {
        errorMessage != nil ? theme.textError : theme.actionPrimary
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            inputBox
            Text(errorMessage ?? "")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textError)
                .padding(.leading, .spacingSM)
                .frame(height: 20, alignment: .center)
            Spacer()
        }
        .frame(height: 76)
    }

    // MARK: - Floating Label

    private var floatingLabelView: some View {
        Text(label)
            .fontOpenSans(isFloatingLabelActive ? .subHeading2 : .subHeading1)
            .foregroundColor(floatingLabelColor)
            .offset(y: isFloatingLabelActive ? -15 : 0)
            .offset(x: 16)
            .animation(.easeInOut(duration: 0.1), value: isFloatingLabelActive)
    }

    // MARK: - Lb Field

    private var lbsField: some View {
        HStack(spacing: 4) {
            TextField("", text: $lbsDisplayValue)
                .keyboardType(.numberPad)
                .focused($lbsFieldFocused)
                .multilineTextAlignment(.trailing)
                .font(.body2)
                .foregroundColor(theme.textBody)
                .frame(minWidth: 30)
                .fixedSize()

            Text(lang.lbsUnit)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textSubheading)
        }
    }

    // MARK: - Oz Field

    private var ozField: some View {
        HStack(spacing: 4) {
            TextField("", text: $ozDisplayValue)
                .keyboardType(.numberPad)
                .focused($ozFieldFocused)
                .multilineTextAlignment(.trailing)
                .font(.body2)
                .foregroundColor(theme.textBody)
                .frame(minWidth: 30)
                .fixedSize()

            Text(lang.ozUnit)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textSubheading)
        }
    }

    // MARK: - Inner Content

    private var innerContent: some View {
        HStack(spacing: 0) {
            Spacer()
            lbsField
            Spacer().frame(width: .spacingMD)
            ozField
                .padding(.trailing, .spacingSM)
        }
        .padding(.top, isFloatingLabelActive ? 15 : 0)
        .accentColor(accentColor)
    }

    // MARK: - Input Box

    private var inputBox: some View {
        ZStack(alignment: .leading) {
            floatingLabelView
            innerContent
        }
        .frame(height: 56)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .onTapGesture {
            if !lbsFieldFocused && !ozFieldFocused {
                lbsFieldFocused = true
            }
        }
        .onChange(of: focusedField) { _, newValue in
            lbsFieldFocused = (newValue == .babyBirthWeight)
            ozFieldFocused = (newValue == .babyBirthWeightOz)
        }
        .onChange(of: lbsFieldFocused) { _, focused in
            if focused {
                focusedField = .babyBirthWeight
            } else if !ozFieldFocused && focusedField == .babyBirthWeight {
                focusedField = nil
            }
        }
        .onChange(of: ozFieldFocused) { _, focused in
            if focused {
                focusedField = .babyBirthWeightOz
            } else if !lbsFieldFocused && focusedField == .babyBirthWeightOz {
                focusedField = nil
            }
        }
        .onChange(of: lbsDisplayValue) { oldValue, newValue in
            handleDisplayChange(
                oldValue: oldValue,
                newValue: newValue,
                display: $lbsDisplayValue,
                source: $lbsValue,
                formatter: lbsFormatter
            )
        }
        .onChange(of: lbsValue) { oldValue, newValue in
            guard oldValue != newValue else { return }
            let formatted = lbsFormatter.formatInput(newValue)
            if lbsDisplayValue != formatted { lbsDisplayValue = formatted }
        }
        .onChange(of: ozDisplayValue) { oldValue, newValue in
            handleDisplayChange(
                oldValue: oldValue,
                newValue: newValue,
                display: $ozDisplayValue,
                source: $ozValue,
                formatter: ozFormatter
            )
        }
        .onChange(of: ozValue) { oldValue, newValue in
            guard oldValue != newValue else { return }
            let formatted = ozFormatter.formatInput(newValue)
            if ozDisplayValue != formatted { ozDisplayValue = formatted }
        }
        .onAppear {
            initializeDisplay(source: lbsValue, display: &lbsDisplayValue, formatter: lbsFormatter)
            initializeDisplay(source: ozValue, display: &ozDisplayValue, formatter: ozFormatter)
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                if lbsFieldFocused {
                    Button(CommonStrings.next) {
                        focusedField = .babyBirthWeightOz
                    }
                }
                if ozFieldFocused {
                    Button(CommonStrings.done) {
                        focusedField = nil
                    }
                }
            }
        }
    }

    // MARK: - Formatting Helpers

    private func initializeDisplay(source: String, display: inout String, formatter: MetricFieldFormatter) {
        if source.isEmpty {
            display = ""
        } else {
            display = formatter.formatInput(source)
        }
    }

    private func handleDisplayChange(
        oldValue: String,
        newValue: String,
        display: Binding<String>,
        source: Binding<String>,
        formatter: MetricFieldFormatter
    ) {
        if newValue.isEmpty {
            display.wrappedValue = ""
            source.wrappedValue = ""
            return
        }
        let formatted = formatter.formatInput(newValue)
        guard formatter.shouldUpdateValue(from: oldValue, to: newValue) else {
            display.wrappedValue = oldValue
            return
        }
        if display.wrappedValue != formatted { display.wrappedValue = formatted }
        if source.wrappedValue != formatted { source.wrappedValue = formatted }
    }
}
