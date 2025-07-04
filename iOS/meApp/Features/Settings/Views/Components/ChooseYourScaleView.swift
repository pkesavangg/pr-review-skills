//
//  ChooseYourScaleView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import SwiftUI
// MARK: - Choose Your Scale sheet
/// Stand-alone wrapper that hosts `ScaleManualListView` inside a modal sheet.
/// Dismisses itself when the user taps the close icon or selects a scale.
struct ChooseYourScaleView: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    let lang = MyScaleStrings.self
    /// Callback invoked when the user selects a scale in the list.
    var onSelect: (ScaleItemInfo) -> Void

    var body: some View {
        VStack(spacing: .spacingXS) {
            NavbarHeaderView(
                title: lang.chooseYourScale,
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: { EmptyView() },
                onLeadingTap: { dismiss() },
                onTrailingTap: {},
                canShowBorder: true,
                canShowPresentationIndicator: true
            )

            // Scale list
            ScaleManualListView { scale in
                onSelect(scale)
                dismiss()
            }
            .padding(.top, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}

#Preview(body: {
    ChooseYourScaleView { scale in
        // Handle scale selection
    }
})
