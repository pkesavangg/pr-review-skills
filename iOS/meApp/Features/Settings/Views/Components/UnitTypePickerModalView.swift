//
//  UnitTypePickerModalView.swift
//  meApp
//

import SwiftUI

/// Radio-style Unit Type dialog presented via `notificationService.showModal`.
///
/// Both sections ("My Weight" and "My Kids") are always visible. A section whose
/// backing product is absent renders **locked** — greyed out, non-editable, and
/// pinned to the system default unit (per the Me App 2.0 "Unit Type" design):
/// - "My Weight" is editable when a weight scale is present, otherwise locked.
/// - "My Kids" is editable when a baby scale or baby profile is present, otherwise locked.
///
/// `onSave` returns both values; the caller persists only the section(s) that are enabled,
/// so a locked section's default is never written back to the account.
struct UnitTypePickerModalView: View {
    @Environment(\.appTheme) private var theme

    /// System default units used to display (and pin) a locked section.
    static let defaultWeightUnit: WeightUnit = .lb
    static let defaultMeasurementUnits: MeasurementUnits = .imperialLbOz

    let isMyWeightEnabled: Bool
    let isMyKidsEnabled: Bool
    let onCancel: () -> Void
    let onSave: (WeightUnit, MeasurementUnits) -> Void

    @State private var selectedWeightUnit: WeightUnit
    @State private var selectedMeasurementUnits: MeasurementUnits

    private let lang = SettingsStrings.UnitType.self
    private let commonLang = CommonStrings.self

    init(
        isMyWeightEnabled: Bool,
        isMyKidsEnabled: Bool,
        selectedWeightUnit: WeightUnit,
        selectedMeasurementUnits: MeasurementUnits,
        onCancel: @escaping () -> Void,
        onSave: @escaping (WeightUnit, MeasurementUnits) -> Void
    ) {
        self.isMyWeightEnabled = isMyWeightEnabled
        self.isMyKidsEnabled = isMyKidsEnabled
        self.onCancel = onCancel
        self.onSave = onSave
        // A locked section is pinned to the system default; an enabled one reflects the account.
        _selectedWeightUnit = State(
            initialValue: isMyWeightEnabled ? selectedWeightUnit : Self.defaultWeightUnit
        )
        _selectedMeasurementUnits = State(
            initialValue: isMyKidsEnabled ? selectedMeasurementUnits : Self.defaultMeasurementUnits
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            Text(SettingsStrings.unitType)
                .fontOpenSans(.heading4)
                .foregroundStyle(theme.textHeading)
                .accessibilityAddTraits(.isHeader)

            unitSections

            actionButtons
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .clipShape(.rect(cornerRadius: .radiusXL))
    }

    // MARK: - Layout

    private var unitSections: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(lang.myWeight, isEnabled: isMyWeightEnabled)

            radioRow(
                title: lang.lbFeet,
                isSelected: selectedWeightUnit == .lb,
                isEnabled: isMyWeightEnabled
            ) { selectedWeightUnit = .lb }
            radioRow(
                title: lang.kgCm,
                isSelected: selectedWeightUnit == .kg,
                isEnabled: isMyWeightEnabled
            ) { selectedWeightUnit = .kg }

            Divider()
                .padding(.vertical, .spacingSM)

            sectionHeader(lang.myKids, isEnabled: isMyKidsEnabled)

            radioRow(
                title: lang.lbsOzIn,
                isSelected: selectedMeasurementUnits == .imperialLbOz,
                isEnabled: isMyKidsEnabled
            ) { selectedMeasurementUnits = .imperialLbOz }
            radioRow(
                title: lang.lbsDecimalIn,
                isSelected: selectedMeasurementUnits == .imperialLbDecimal,
                isEnabled: isMyKidsEnabled
            ) { selectedMeasurementUnits = .imperialLbDecimal }
            radioRow(
                title: lang.kgCm,
                isSelected: selectedMeasurementUnits == .metric,
                isEnabled: isMyKidsEnabled
            ) { selectedMeasurementUnits = .metric }
        }
    }

    // MARK: - Components

    private func sectionHeader(_ title: String, isEnabled: Bool) -> some View {
        Text(title)
            .fontOpenSans(.heading5)
            .foregroundStyle(theme.textHeading)
            .opacity(isEnabled ? 1 : lockedOpacity)
            .padding(.bottom, .spacingXS)
    }

    private let lockedOpacity: Double = 0.4

    private func radioRow(
        title: String,
        isSelected: Bool,
        isEnabled: Bool,
        onTap: @escaping () -> Void
    ) -> some View {
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
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : lockedOpacity)
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
                isDisabled: false
            ) {
                onSave(selectedWeightUnit, selectedMeasurementUnits)
            }
        }
    }
}
