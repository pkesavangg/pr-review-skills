//
//  UnitTypePickerModalView.swift
//  meApp
//

import SwiftUI

/// Radio-style Unit Type dialog presented via `notificationService.showModal`.
///
/// Two layouts:
/// - **No baby scale** — a single weight-unit list (`lb & feet` / `kg & cm`) bound to `WeightUnit`.
/// - **With baby scale** — a "My Weight" section (`WeightUnit`) plus a "My Kids" section
///   (`MeasurementUnits`) for the baby-scale readings.
///
/// `onSave` returns both selections; for the no-baby layout `measurementUnits` is passed through unchanged.
struct UnitTypePickerModalView: View {
    @Environment(\.appTheme) private var theme

    let showMyKids: Bool
    let onCancel: () -> Void
    let onSave: (WeightUnit, MeasurementUnits) -> Void

    @State private var selectedWeightUnit: WeightUnit
    @State private var selectedMeasurementUnits: MeasurementUnits

    private let lang = SettingsStrings.UnitType.self
    private let commonLang = CommonStrings.self

    init(
        showMyKids: Bool,
        selectedWeightUnit: WeightUnit,
        selectedMeasurementUnits: MeasurementUnits,
        onCancel: @escaping () -> Void,
        onSave: @escaping (WeightUnit, MeasurementUnits) -> Void
    ) {
        self.showMyKids = showMyKids
        self.onCancel = onCancel
        self.onSave = onSave
        _selectedWeightUnit = State(initialValue: selectedWeightUnit)
        _selectedMeasurementUnits = State(initialValue: selectedMeasurementUnits)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            Text(SettingsStrings.unitType)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .accessibilityAddTraits(.isHeader)

            if showMyKids {
                babyScaleLayout
            } else {
                weightOnlyLayout
            }

            actionButtons
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXL)
    }

    // MARK: - Layouts

    private var weightOnlyLayout: some View {
        VStack(alignment: .leading, spacing: 0) {
            radioRow(
                title: lang.lbFeet,
                isSelected: selectedWeightUnit == .lb
            ) { selectedWeightUnit = .lb }
            radioRow(
                title: lang.kgCm,
                isSelected: selectedWeightUnit == .kg
            ) { selectedWeightUnit = .kg }
        }
    }

    private var babyScaleLayout: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            // My Weight
            sectionHeader(lang.myWeight)
            VStack(alignment: .leading, spacing: 0) {
                radioRow(
                    title: lang.lbsIn,
                    isSelected: selectedWeightUnit == .lb
                ) { selectedWeightUnit = .lb }
                radioRow(
                    title: lang.metricCm,
                    isSelected: selectedWeightUnit == .kg
                ) { selectedWeightUnit = .kg }
            }

            Divider().background(theme.statusUtilityPrimary)

            // My Kids
            sectionHeader(lang.myKids)
            VStack(alignment: .leading, spacing: 0) {
                radioRow(
                    title: lang.lbsOzIn,
                    isSelected: selectedMeasurementUnits == .imperialLbOz
                ) { selectedMeasurementUnits = .imperialLbOz }
                radioRow(
                    title: lang.lbsDecimalIn,
                    isSelected: selectedMeasurementUnits == .imperialLbDecimal
                ) { selectedMeasurementUnits = .imperialLbDecimal }
                radioRow(
                    title: lang.metricCm,
                    isSelected: selectedMeasurementUnits == .metric
                ) { selectedMeasurementUnits = .metric }
            }
        }
    }

    // MARK: - Components

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .fontOpenSans(.body1)
            .bold()
            .foregroundColor(theme.textHeading)
    }

    private func radioRow(title: String, isSelected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: .spacingSM) {
                ZStack {
                    Circle()
                        .strokeBorder(
                            isSelected ? theme.actionPrimary : theme.textBody.opacity(0.4),
                            lineWidth: 2
                        )
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle()
                            .fill(theme.actionPrimary)
                            .frame(width: 12, height: 12)
                    }
                }
                Text(title)
                    .fontOpenSans(.body1)
                    .foregroundColor(theme.textHeading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .padding(.vertical, .spacingSM)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var actionButtons: some View {
        HStack(spacing: .spacingMD) {
            Spacer()
            ButtonView(
                text: commonLang.cancel,
                type: .inlineTextTertiary,
                size: .small,
                isDisabled: false,
                action: onCancel
            )
            ButtonView(
                text: commonLang.save,
                type: .inlineTextPrimary,
                size: .small,
                isDisabled: false,
                action: { onSave(selectedWeightUnit, selectedMeasurementUnits) }
            )
        }
    }
}
