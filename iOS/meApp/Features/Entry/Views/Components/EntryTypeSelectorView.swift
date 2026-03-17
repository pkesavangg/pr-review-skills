//
//  EntryTypeSelectorView.swift
//  meApp
//

import SwiftUI

struct EntryTypeSelectorView: View {
    @Environment(\.appTheme) private var theme
    @Binding var selectedType: ManualEntryType

    var body: some View {
        HStack {
            Spacer()
            Menu {
                ForEach(ManualEntryType.allCases, id: \.self) { type in
                    Button(type.rawValue) {
                        selectedType = type
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(selectedType.rawValue)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(theme.actionSecondary)
                    AppIconView(icon: AppAssets.chevronDown, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.actionSecondary)
                }
            }
            Spacer()
        }
        .frame(height: 56)
        .background(theme.backgroundPrimary)
        .border(sides: [.bottom], thickness: 0.5)
    }
}
