//
//  CompleteProfileSetupFormView.swift
//  meApp
//
//  Complete Profile Setup step UI, shared across the Bluetooth scale-setup flows
//  (A6/LCBT and Bluetooth/A3). Shown only when the account is missing the biological
//  sex / height the weight product requires. Pre-filled from the account (or defaults);
//  the owning flow's footer drives Next (save) / Skip / Back.
//
//  Unlike the BtWifi/R4 (0412) flow's own Complete Profile screen, the A3/A6 variant
//  omits the optional "Set a Goal" section — only biological sex + height are collected.
//

import SwiftUI

struct CompleteProfileSetupFormView: View {
    @ObservedObject var store: CompleteProfileSetupStore
    @Environment(\.appTheme) private var theme

    private let lang = ScaleSetupStrings.CompleteProfileStrings.self
    private let identityDisplay: (String) -> String = { $0 }
    private let capitalizedSexDisplay: (Sex) -> String = { $0.rawValue.capitalized }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingMD) {
                header
                genderRow
                heightRow
            }
            .padding(.top, .spacingLG)
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively)
        .pickerSheet(
            isPresented: $store.showProfileGenderPicker,
            selectedValues: [store.profileGender],
            // "private" is not offered during scale-setup profile completion.
            options: [Sex.allCases.filter { $0 != .private }],
            displayValue: capitalizedSexDisplay,
            title: lang.biologicalSex
        ) { vals in
            if let sex = vals.first {
                store.updateProfileGender(sex)
            }
        }
        .pickerSheet(
            isPresented: $store.showProfileHeightInchesPicker,
            selectedValues: store.selectedProfileHeightInches,
            options: ConversionTools.heightInchesOptions,
            displayValue: identityDisplay,
            pickerType: .heightInches,
            title: lang.height
        ) { newValues in
            store.updateProfileHeight(fromMetric: false, values: newValues)
        }
        .pickerSheet(
            isPresented: $store.showProfileHeightCmPicker,
            selectedValues: store.selectedProfileHeightCm,
            options: ConversionTools.heightCmOptions,
            displayValue: identityDisplay,
            pickerType: .heightCm,
            title: lang.height
        ) { newValues in
            store.updateProfileHeight(fromMetric: true, values: newValues)
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(lang.title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(lang.subtitle)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }

    private var genderRow: some View {
        ActionListItemView(config: ActionListItemConfig(
            title: lang.biologicalSex,
            value: store.profileGenderText,
            chevronType: .upDown) { store.showProfileGenderPicker = true })
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingXS / 2)
            .background(theme.backgroundPrimary)
            .cornerRadius(8)
            .appAccessibility(id: AccessibilityID.scaleSetupProfileGenderRow)
    }

    private var heightRow: some View {
        ActionListItemView(config: ActionListItemConfig(
            title: lang.height,
            value: store.profileHeightText,
            chevronType: .upDown) { store.presentProfileHeightPicker() })
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingXS / 2)
            .background(theme.backgroundPrimary)
            .cornerRadius(8)
            .appAccessibility(id: AccessibilityID.scaleSetupProfileHeightRow)
    }
}
