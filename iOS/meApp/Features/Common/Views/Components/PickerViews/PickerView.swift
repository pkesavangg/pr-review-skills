//
//  PickerView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//

import SwiftUI

// MARK: - Picker View
/// A picker view that allows multiple selections with a customizable appearance.
/// This view supports a dynamic number of options, each with its own set of selectable values.
struct PickerView<T: Hashable>: View {
    @Environment(\.appTheme) private var theme
    @State private var tempSelectedValues: [T]
    public let selectedValues: [T]
    public let options: [[T]]
    public let displayValue: (T) -> String
    public let pickerType: PickerType
    public let title: String?
    public let showCancel: Bool
    public var updateValues: (([T]) -> Void)?
    public var onCancel: (() -> Void)?
    
    let commonLang = CommonStrings.self
    init(
        selectedValues: [T],
        options: [[T]],
        displayValue: @escaping (T) -> String,
        pickerType: PickerType = .default,
        title: String? = nil,
        showCancel: Bool = false,
        updateValues: (([T]) -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        self.selectedValues = selectedValues
        self.options = options
        self.displayValue = displayValue
        self.pickerType = pickerType
        self.title = title
        self.showCancel = showCancel
        self.updateValues = updateValues
        self.onCancel = onCancel
        self._tempSelectedValues = State(initialValue: selectedValues)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Header with Cancel and Select buttons
            ZStack {
                if let title = title {
                    Text(title)
                        .fontOpenSans(.heading5)
                        .foregroundColor(theme.textHeading)
                        .lineLimit(1)
                        .accessibilityAddTraits(.isHeader)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                
                HStack {
                    if showCancel, onCancel != nil {
                        ButtonView(
                            text: commonLang.cancel,
                            type: .inlineTextTertiary,
                            size: .small,
                            isDisabled: false
                        ) {
                            onCancel?()
                        }
                    }
                    Spacer()
                    
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !isHeightValid
                    ) {
                        updateValues?(tempSelectedValues)
                    }
                }
                
            }
            .padding()
            .padding(.bottom, .spacingLG)
            
            // Picker Section
            ZStack {
                // Selection background
                RoundedRectangle(cornerRadius: .radiusSM)
                    .fill(theme.backgroundSecondary)
                    .frame(height: 35)
                    .allowsHitTesting(false)
                    .padding(.horizontal)
                
                HStack(spacing: 0) {
                    ForEach(0..<tempSelectedValues.count, id: \.self) { index in
                        HStack(spacing: -20) {
                            pickerView(
                                selection: $tempSelectedValues[index],
                                options: options[index],
                                displayValue: displayValue
                            )
                            
                            // Fixed symbol based on picker type and column
                            if let symbol = getSymbol(for: index) {
                                Text(symbol)
                                    .fontOpenSans(.body1)
                                    .foregroundColor(theme.textHeading)
                                    .offset(x: pickerType == .heightInches ? -15 : 0 )
                            }
                        }
                        .frame(width: columnWidth(), alignment: .leading)
                    }
                }
            }
            .frame(height: 180)
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .onAppear {
            tempSelectedValues = selectedValues
        }
    }
    
    private func getSymbol(for index: Int) -> String? {
        switch pickerType {
        case .heightInches:
            return index == 0 ? "′" : "″"
        case .heightCm:
            return index == tempSelectedValues.count - 1 ? "cm" : nil
        default:
            return nil
        }
    }
    
    @ViewBuilder
    private func pickerView(selection: Binding<T>, options: [T], displayValue: @escaping (T) -> String) -> some View {
        PickerWithoutIndicatorsView(selection: selection) {
            ForEach(options, id: \.self) { value in
                let isSelected = value == selection.wrappedValue
                Text(displayValue(value))
                    .fontOpenSans(.body1)
                    .fontWeight(isSelected ? .bold : .regular)
                    .foregroundColor(theme.textHeading.opacity(isSelected ? 1 : 0.6))
                    .tag(value)
            }
        }
    }
    
    private func columnWidth() -> CGFloat? {
        switch pickerType {
        case .heightInches, .heightCm:
            return 80
        default:
            return nil // flexible width to avoid text truncation
        }
    }
    
    /// Validates if the current height picker selection is valid.
    /// Returns true for non-height pickers or when height is valid.
    private var isHeightValid: Bool {
        guard pickerType == .heightInches || pickerType == .heightCm else {
            return true // Not a height picker, always valid
        }
        
        let stringValues = tempSelectedValues.map { displayValue($0) }
        let fromMetric = pickerType == .heightCm
        return ConversionTools.isValidHeightPickerValues(fromMetric: fromMetric, values: stringValues)
    }
}

// Example usage in PickerTestView
struct PickerTestView: View {
    @State private var selectedTime: [String] = ["8", "00", "PM"]
    @State private var selectedHeightInches: [String] = ["5", "8"]
    @State private var selectedHeightCm: [String] = ["1", "8", "0"]
    @State private var showTimePicker = false
    @State private var showHeightInchesPicker = false
    @State private var showHeightCmPicker = false
    
    private let timeOptions: [[String]] = [
        (1...12).map { "\($0)" },
        (0...59).map { String(format: "%02d", $0) },
        ["AM", "PM"]
    ]
    
    private let heightInchesOptions: [[String]] = [
        (3...7).map { "\($0)" },
        (0...11).map { "\($0)" }
    ]
    
    private let heightCmOptions: [[String]] = [
        (1...2).map { "\($0)" },
        (0...9).map { "\($0)" },
        (0...9).map { "\($0)" }
    ]
    
    var body: some View {
        VStack(spacing: 40) {
            // Time picker button
            VStack(spacing: 20) {
                Text("Selected Time: \(selectedTime.joined(separator: ":"))")
                    .font(.title2)
                
                Button("Select Time") {
                    showTimePicker = true
                }
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            
            // Height in inches picker button
            VStack(spacing: 20) {
                Text("Selected Height (inches): \(selectedHeightInches[0])′ \(selectedHeightInches[1])″")
                    .font(.title2)
                
                Button("Select Height (inches)") {
                    showHeightInchesPicker = true
                }
                .padding()
                .background(Color.green)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            
            // Height in cm picker button
            VStack(spacing: 20) {
                Text("Selected Height (cm): \(selectedHeightCm.joined()) cm")
                    .font(.title2)
                
                Button("Select Height (cm)") {
                    showHeightCmPicker = true
                }
                .padding()
                .background(Color.orange)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
        }
        .pickerSheet(
            isPresented: $showTimePicker,
            selectedValues: selectedTime,
            options: timeOptions,
            displayValue: { $0 },
            onUpdate: { selectedTime = $0 }
        )
        .pickerSheet(
            isPresented: $showHeightInchesPicker,
            selectedValues: selectedHeightInches,
            options: heightInchesOptions,
            displayValue: { $0 },
            pickerType: .heightInches,
            onUpdate: { selectedHeightInches = $0 } // swiftlint:disable:this trailing_closure
        )
        .pickerSheet(
            isPresented: $showHeightCmPicker,
            selectedValues: selectedHeightCm,
            options: heightCmOptions,
            displayValue: { $0 },
            pickerType: .heightCm,
            onUpdate: { selectedHeightCm = $0 } // swiftlint:disable:this trailing_closure
        )
    }
}

struct PickerTestView_Previews: PreviewProvider {
    static var previews: some View {
        PickerTestView()
    }
}
