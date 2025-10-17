//
//  ManualEntryScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import SwiftUI

// MARK: - ManualEntryScreen
/// A view for manual entry of body metrics and other related information.
/// This screen allows users to input various body metrics such as weight, BMI, body fat, and more.
struct ManualEntryScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject private var entryStore = EntryStore()
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    @State private var focusedField: FocusField?
    // Keyboard observer to adjust bottom padding when the keyboard is visible
    @StateObject private var keyboard = KeyboardResponder()
    
    let manualEntryLang = ManualEntryStrings.self
    let commonLang = CommonStrings.self
    let labels = InputFieldLabels.self
    let appAssets = AppAssets.self
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<EmptyView, EmptyView>(title: manualEntryLang.title, canShowBorder: true)
            
            ScrollView(.vertical) {
                VStack(spacing: .spacingLG) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        // Weight Input Field
                        MetricInputField(
                            config: TextInputConfig(
                                label: labels.weightLabel(entryStore.weightUnit == .kg),
                                inputType: .metric,
                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.weight),
                                focusField: .weight,
                                maxLength: 4,
                                maxValue: 999.9
                            ),
                            value: $entryStore.manualEntryForm.weight.value,
                            focusedField: $focusedField
                        ) {
                            focusedField = nil
                        }
                        
                        Text(labels.date)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                        
                        HStack(spacing: .spacingSM) {
                            DateLabelView(date: entryStore.manualEntryForm.date.value,
                                          isSelected: entryStore.showDatePicker
                            ) {
                                withAnimation { entryStore.showDatePicker.toggle()
                                }
                            }
                            TimeLabelView(time: entryStore.manualEntryForm.time.value,
                                          isSelected: entryStore.showTimePicker) {
                                withAnimation {
                                    entryStore.showTimePicker.toggle()
                                }
                            }
                        }
                        
                        // Pickers
                        DatePickerView(isPresented: $entryStore.showDatePicker,
                                       date: $entryStore.manualEntryForm.date.value,
                                       endDate: Date())
                        
                        TimePickerView(isPresented: $entryStore.showTimePicker,
                                       time: $entryStore.manualEntryForm.time.value,
                                       selectedDate: entryStore.manualEntryForm.date.value,
                                       endTime: entryStore.maxSelectableTime)
                    }
                    
                    // Accordion header
                    VStack(spacing: 0) {
                        VStack {
                            HStack {
                                Text(manualEntryLang.bodyMetrics)
                                    .fontOpenSans(.heading4)
                                    .foregroundColor(theme.textHeading)
                                Spacer()
                                AppIconView(icon: entryStore.showMetrics ? appAssets.chevronUp: appAssets.chevronDown,
                                            size: IconSize(width: 32, height: 32))
                                .foregroundColor(theme.actionPrimary)
                            }
                            
                            Text("(\(commonLang.optional))")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textSubheading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            withAnimation {
                                entryStore.showMetrics.toggle()
                            }
                        }
                        .padding(.bottom, .spacingXS)
                        
                        if entryStore.showMetrics {
                            VStack(spacing: .spacingSM) {
                                // Input fields for body metrics
                                
                                // BMI field
                                MetricInputField(
                                    config: TextInputConfig(label: labels.bmi,
                                                            inputType: .metric,
                                                            errorMessage: entryStore.getError(for: entryStore.manualEntryForm.bmi), focusField: .bmi,
                                                            maxLength: 3,
                                                            maxValue: 99.9),
                                    value: $entryStore.manualEntryForm.bmi.value,
                                    focusedField: $focusedField
                                ) {
                                    focusedField = .bodyFat
                                }
                                .onChange(of: focusedField) { _, newValue in
                                    if newValue == .bmi {
                                        entryStore.disableBmiAutoCalculation()
                                    }
                                }
                                
                                // Body Fat field
                                MetricInputField(
                                    config: TextInputConfig(label: labels.bodyFat,
                                                            inputType: .metric,
                                                            errorMessage: entryStore.getError(for: entryStore.manualEntryForm.bodyFat),
                                                            focusField: .bodyFat,
                                                            maxLength: 3,
                                                            maxValue: 99.9),
                                    value: $entryStore.manualEntryForm.bodyFat.value,
                                    focusedField: $focusedField
                                ) {
                                    focusedField = .muscleMass
                                }
                                
                                // Muscle Mass field
                                MetricInputField(
                                    config: TextInputConfig(label: labels.muscleMass,
                                                            inputType: .metric,
                                                            errorMessage: entryStore.getError(for: entryStore.manualEntryForm.muscleMass),
                                                            focusField: .muscleMass,
                                                            maxLength: 3,
                                                            maxValue: 99.9),
                                    value: $entryStore.manualEntryForm.muscleMass.value,
                                    focusedField: $focusedField
                                ) {
                                    focusedField = .bodyWater
                                }
                                
                                MetricInputField(
                                    config: TextInputConfig(label: labels.bodyWater,
                                                            inputType: .metric,
                                                            errorMessage: entryStore.getError(for: entryStore.manualEntryForm.bodyWater),
                                                            focusField: .bodyWater,
                                                            maxLength: 3,
                                                            maxValue: 99.9),
                                    value: $entryStore.manualEntryForm.bodyWater.value,
                                    focusedField: $focusedField
                                ) {
                                    focusedField = nil
                                }
                                
                                // Other body metrics fields
                                if entryStore.canShowOtherBodyMetrics {
                                    // Heart Rate field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.heartRate,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.heartRate),
                                                                focusField: .heartRate,
                                                                maxLength: 3,
                                                                allowWholeNumbers: true),
                                        value: $entryStore.manualEntryForm.heartRate.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .boneMass
                                    }
                                    
                                    // Bone Mass field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.boneMass,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.boneMass),
                                                                focusField: .boneMass,
                                                                maxLength: 3,
                                                                maxValue: 99.9),
                                        value: $entryStore.manualEntryForm.boneMass.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .visceralFat
                                    }
                                    
                                    // Visceral Fat field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.visceralFat,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.visceralFat),
                                                                focusField: .visceralFat,
                                                                maxLength: 2,
                                                                allowWholeNumbers: true),
                                        value: $entryStore.manualEntryForm.visceralFat.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .subcutaneousFat
                                    }
                                    
                                    // Subcutaneous Fat field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.subcutaneousFat,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.subcutaneousFat),
                                                                focusField: .subcutaneousFat,
                                                                maxLength: 3,
                                                                maxValue: 99.9),
                                        value: $entryStore.manualEntryForm.subcutaneousFat.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .protein
                                    }
                                    
                                    // Protein field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.protein,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.protein),
                                                                focusField: .protein,
                                                                maxLength: 3,
                                                                maxValue: 99.9),
                                        value: $entryStore.manualEntryForm.protein.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .skeletalMuscles
                                    }
                                    
                                    // Skeletal Muscles field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.skeletalMuscles,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.skeletalMuscles),
                                                                focusField: .skeletalMuscles,
                                                                maxLength: 3,
                                                                maxValue: 99.9),
                                        value: $entryStore.manualEntryForm.skeletalMuscles.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .bmr
                                    }
                                    
                                    // Basal Metabolic Rate field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.basalMetabolicRate,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.bmr),
                                                                focusField: .bmr,
                                                                maxLength: 5,
                                                                allowWholeNumbers: true),
                                        value: $entryStore.manualEntryForm.bmr.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = .metabolicAge
                                    }
                                    
                                    // Metabolic Age field
                                    MetricInputField(
                                        config: TextInputConfig(label: labels.metabolicAge,
                                                                inputType: .metric,
                                                                errorMessage: entryStore.getError(for: entryStore.manualEntryForm.metabolicAge),
                                                                focusField: .metabolicAge,
                                                                maxLength: 3,
                                                                allowWholeNumbers: true),
                                        value: $entryStore.manualEntryForm.metabolicAge.value,
                                        focusedField: $focusedField
                                    ) {
                                        focusedField = nil
                                        Task {
                                            guard !entryStore.isSaving else { return }
                                            await entryStore.saveEntry()
                                            performTabSwitchAndHideKeyboard()
                                        }
                                    }
                                }
                            }
                            .padding(.top, .spacingSM)
                        }
                    }
                    
                    // Save button
                    ButtonView(
                        text: commonLang.save,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !entryStore.manualEntryForm.isValid || entryStore.isSaving
                    ) {
                        Task {
                            focusedField = nil
                            await entryStore.saveEntry()
                            performTabSwitchAndHideKeyboard()
                        }
                    }
                    
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingLG)
                .padding(.bottom, keyboard.currentHeight)
                .onAppear {
                    // Ensure time defaults to current when landing on this tab
                    entryStore.refreshTimeOnTabSelected()
                    // Start periodic time sync while on this screen
                    entryStore.startAutoTimeSync()
                    // Register a handler that decides whether the tab can be left.
                    registerDeactivation {
                        // If the form is clean we can leave immediately.
                        guard entryStore.manualEntryForm.isDirty else { return true }
                        // Otherwise ask the user via the store's confirmation helper.
                        return await entryStore.confirmDiscardChanges()
                    }
                }
                .onDisappear {
                    // Stop periodic time sync when leaving screen
                    entryStore.stopAutoTimeSync()
                }
                .onChange(of: tabViewModel.selectedTab) { _, newValue in
                    // Update time every time Entry tab becomes active
                    if newValue == .entry {
                        entryStore.refreshTimeOnTabSelected()
                        entryStore.startAutoTimeSync()
                    } else {
                        entryStore.stopAutoTimeSync()
                    }
                    // Pre-populate form if coming from AppSync **Edit** flow
                    if let metrics = tabViewModel.pendingAppSyncEditMetrics, newValue == .entry {
                        entryStore.populateFromAppSync(metrics: metrics)
                        tabViewModel.pendingAppSyncEditMetrics = nil
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(theme.backgroundSecondary)
        .animation(.easeOut(duration: 0.25), value: keyboard.currentHeight)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(commonLang.done) {
                    withAnimation {
                        focusedField = nil
                    }
                }
            }
        }
    }
    
    private func performTabSwitchAndHideKeyboard() {
        hideKeyboard()
        // Let the keyboard dismissal/animation settle before a big tab switch.
        Task { @MainActor in
            tabViewModel.selectTab(.dash)
        }
    }
    
}

#Preview {
    ManualEntryScreen()
        .environmentObject(BottomTabBarViewModel())
        .environmentObject(Theme.shared)
}
