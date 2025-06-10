//
//  CustomPickerView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//

import SwiftUI

// MARK: CustomPickerView
/// A custom picker view that allows multiple selections with a customizable appearance.
/// This view supports a dynamic number of options, each with its own set of selectable values.
struct CustomPickerView<T: Hashable>: View {
    @Environment(\.appTheme) private var theme
    @State private var tempSelectedValues: [T]
    public let selectedValues: [T]
    public let options: [[T]]
    public let displayValue: (T) -> String
    public let title: String?
    public var updateValues: (([T]) -> Void)?
    public var onCancel: (() -> Void)?
    
    init(
        selectedValues: [T],
        options: [[T]],
        displayValue: @escaping (T) -> String,
        title: String? = nil,
        updateValues: (([T]) -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        self.selectedValues = selectedValues
        self.options = options
        self.displayValue = displayValue
        self.title = title
        self.updateValues = updateValues
        self.onCancel = onCancel
        self._tempSelectedValues = State(initialValue: selectedValues)
    }
    
    var body: some View {
        ZStack {
            theme.backgroundPrimary
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header with Cancel and Select buttons
                HStack {
                    // Need to update the buttons with custom button components
                    Button("Cancel") {
                        onCancel?()
                    }
                    .foregroundColor(theme.actionPrimary)
                    
                    Spacer()
                                    
                    Button("Select") {
                        updateValues?(tempSelectedValues)
                    }
                    .foregroundColor(theme.actionPrimary)
                    .fontWeight(.semibold)
                }
                .padding()
                
                
                // Picker Section
                ZStack {
                    //Selection background
                    RoundedRectangle(cornerRadius: .radiusSM)
                        .fill(theme.backgroundSecondary)
                        .frame(height: 35)
                        .allowsHitTesting(false)
                        .padding(.horizontal)
                    
                    HStack(spacing: 0) {
                        ForEach(0..<tempSelectedValues.count, id: \.self) { index in
                            pickerView(
                                selection: $tempSelectedValues[index],
                                options: options[index],
                                displayValue: displayValue
                            )
                            .frame(width: 100, alignment: .leading)
                        }
                    }
                }
                .frame(height: 200)
            }
        }
        .onAppear {
            tempSelectedValues = selectedValues
        }
    }
    
    @ViewBuilder
    private func pickerView(selection: Binding<T>, options: [T], displayValue: @escaping (T) -> String) -> some View {
        PickerWithoutIndicatorsView(selection: selection) {
            ForEach(options, id: \.self) { value in
                Text(displayValue(value))
                    .fontOpenSans(.body1)
                    .fontWeight(value == selection.wrappedValue ? .semibold : .regular)
                    // TODO: Need to update the foreground color
                    .foregroundColor(theme.textHeading.opacity(value == selection.wrappedValue ? 1 : 0.6))
                    .tag(value)
            }
        }
    }
}


struct PickerTestView: View {
    @State private var selectedTime: [String] = ["8", "00", "PM"]
    @State private var selectedHeight: [String] = ["5′", "8″"]
    @State private var showTimePicker = false
    @State private var showHeightPicker = false
    
    private let timeOptions: [[String]] = [
        (1...12).map { "\($0)" },
        (0...59).map { String(format: "%02d", $0) },
        ["AM", "PM"]
    ]
    
    private let heightOptions: [[String]] = [
        (3...7).map { "\($0)′" },
        (0...11).map { "\($0)″" }
    ]
    
    var body: some View {
        VStack(spacing: 40) {
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
            
            VStack(spacing: 20) {
                Text("Selected Height: \(selectedHeight.joined(separator: " "))")
                    .font(.title2)
                
                Button("Select Height") {
                    showHeightPicker = true
                }
                .padding()
                .background(Color.green)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
        }
        .sheet(isPresented: $showTimePicker) {
            CustomPickerView(
                selectedValues: selectedTime,
                options: timeOptions,
                displayValue: { $0 },
                updateValues: { newValues in
                    selectedTime = newValues
                    showTimePicker = false
                },
                onCancel: {
                    showTimePicker = false
                }
            )
            .presentationDetents([.height(300)])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled()
        }
        .sheet(isPresented: $showHeightPicker) {
            CustomPickerView(
                selectedValues: selectedHeight,
                options: heightOptions,
                displayValue: { $0 },
                updateValues: { newValues in
                    selectedHeight = newValues
                    showHeightPicker = false
                },
                onCancel: {
                    showHeightPicker = false
                }
            )
            .presentationDetents([.height(300)])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled()
        }
    }
}

struct PickerTestView_Previews: PreviewProvider {
    static var previews: some View {
        PickerTestView()
    }
}
