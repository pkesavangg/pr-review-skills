//
//  UnitTypePickerModalView.swift
//  meApp
//

import SwiftUI

/// Radio-style Unit Type dialog presented via `notificationService.showModal`.
///
/// Three layouts driven by `UnitDisplayMode`:
/// - `.myWeight` — a single weight-unit list (`lb & feet` / `kg & cm`) bound to `WeightUnit`.
/// - `.myKids`   — a "My Kids" section only (`MeasurementUnits`) for baby-scale readings.
/// - `.both`     — both sections with a divider; shown when weight scale + baby scale are paired.
///                 "My Weight" changes apply to the weight scale; "My Kids" changes apply to the baby scale.
///
/// `onSave` always returns both values; the unchanged unit is passed through unmodified.
struct UnitTypePickerModalView: View {
    @Environment(\.appTheme) private var theme

    enum UnitDisplayMode {
        case myWeight
        case myKids
        case both
    }

    let mode: UnitDisplayMode
    let onCancel: () -> Void
    let onSave: (WeightUnit, MeasurementUnits) -> Void

    @State private var selectedWeightUnit: WeightUnit
    @State private var selectedMeasurementUnits: MeasurementUnits

    private let lang = SettingsStrings.UnitType.self
    private let commonLang = CommonStrings.self

    init(
        mode: UnitDisplayMode,
        selectedWeightUnit: WeightUnit,
        selectedMeasurementUnits: MeasurementUnits,
        onCancel: @escaping () -> Void,
        onSave: @escaping (WeightUnit, MeasurementUnits) -> Void
    ) {
        self.mode = mode
        self.onCancel = onCancel
        self.onSave = onSave
        _selectedWeightUnit = State(initialValue: selectedWeightUnit)
        _selectedMeasurementUnits = State(initialValue: selectedMeasurementUnits)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            Text(SettingsStrings.unitType)
                .fontOpenSans(.heading4)
                .foregroundStyle(theme.textHeading)
                .accessibilityAddTraits(.isHeader)

            switch mode {
            case .myWeight: weightOnlyLayout
            case .myKids:   kidsOnlyLayout
            case .both:     bothLayout
            }

            actionButtons
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .clipShape(.rect(cornerRadius: .radiusXL))
    }

    // MARK: - Layouts

    private var bothLayout: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(lang.myWeight)
                .fontOpenSans(.heading5)
                .foregroundStyle(theme.textHeading)
                .padding(.bottom, .spacingXS)

            radioRow(
                title: lang.lbsIn,
                isSelected: selectedWeightUnit == .lb
            ) { selectedWeightUnit = .lb }
            radioRow(
                title: lang.metricCm,
                isSelected: selectedWeightUnit == .kg
            ) { selectedWeightUnit = .kg }

            Divider()
                .padding(.vertical, .spacingSM)

            Text(lang.myKids)
                .fontOpenSans(.heading5)
                .foregroundStyle(theme.textHeading)
                .padding(.bottom, .spacingXS)

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

    private var kidsOnlyLayout: some View {
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

    // MARK: - Components

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
                    .foregroundStyle(theme.textHeading)
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
