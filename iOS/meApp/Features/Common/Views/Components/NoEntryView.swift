//
//  NoEntryView.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

struct NoEntryView: View {
    @Environment(\.appTheme) private var theme

    let title: String?
    let description: String?
    let buttonTitle: String
    /// Optional asset name for an icon shown above the title (e.g. the baby icon on the empty baby tabs).
    let iconAsset: String?
    /// Tint applied to `iconAsset`. Defaults to `statusIconPrimary` when nil.
    let iconTint: Color?
    let onButtonTap: () -> Void

    init(
        title: String? = EntryStrings.noEntries,
        description: String? = EntryStrings.toStart,
        buttonTitle: String = CommonStrings.connectScale,
        iconAsset: String? = nil,
        iconTint: Color? = nil,
        onButtonTap: @escaping () -> Void
    ) {
        self.title = title
        self.description = description
        self.buttonTitle = buttonTitle
        self.iconAsset = iconAsset
        self.iconTint = iconTint
        self.onButtonTap = onButtonTap
    }

    var body: some View {
        VStack(spacing: .spacingMD) {
            Spacer()

            if let iconAsset = iconAsset {
                AppIconView(icon: iconAsset, size: IconSize(width: 56, height: 56))
                    .foregroundColor(iconTint ?? theme.statusIconPrimary)
            }

            // Title and description (shown only if non-nil)
            VStack(spacing: .spacingXS) {
                if let title = title {
                    Text(title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                }

                if let description = description {
                    Text(description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
            }

            // Connect scale button
            ButtonView(
                text: buttonTitle,
                type: .filledPrimary,
                size: .large,
                isDisabled: false,
                action: onButtonTap
            )

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.backgroundSecondary)
    }
}

#if DEBUG
struct NoEntryView_Previews: PreviewProvider {
    static var previews: some View {
        NoEntryView(title: nil, description: nil) {}
            .environmentObject(Theme.shared)
    }
}
#endif
