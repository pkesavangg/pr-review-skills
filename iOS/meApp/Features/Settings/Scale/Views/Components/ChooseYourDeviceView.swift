//
//  ChooseYourDeviceView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import SwiftUI
// MARK: - Choose Your Scale sheet
/// Stand-alone wrapper that hosts `DeviceManualListView` inside a modal sheet.
/// Dismisses itself when the user taps the close icon or selects a scale.
struct ChooseYourDeviceView: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    let lang = MyDeviceStrings.self
    /// Callback invoked when the user selects a scale in the list.
    var onSelect: (DeviceItemInfo) -> Void

    var body: some View {
        VStack(spacing: .spacingXS) {
            NavbarHeaderView(
                title: lang.chooseYourScale,
                leadingContent: {
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: { EmptyView() },
                onLeadingTap: { dismiss() },
                onTrailingTap: {},
                canShowBorder: true,
                canShowPresentationIndicator: true
            )

            // Own vertical scrolling inside the sheet to avoid nested scrolls elsewhere
            ScrollView(.vertical, showsIndicators: false) {
                DeviceManualListView { scale in
                    onSelect(scale)
                    dismiss()
                }
                .padding(.top, .spacingSM)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}

#Preview(body: {
    ChooseYourDeviceView { _ in
        // Handle scale selection
    }
})
