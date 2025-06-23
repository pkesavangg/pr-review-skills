//
//  NavBarHeaderView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

struct NavbarHeaderView<Leading: View, Trailing: View>: View {
    @Environment(\.appTheme) var theme

    var title: String?
    var leadingContent: (() -> Leading)?
    var trailingContent: (() -> Trailing)?
    var onLeadingTap: (() -> Void)?
    var onTrailingTap: (() -> Void)?
    var canShowBorder = false

    var body: some View {
        ZStack {
            // Center Title
            if let title = title {
                Text(title)
                    .fontOpenSans(.heading5)
                    .fontWeight(.bold)
                    .foregroundColor(theme.actionSecondary)
                    .lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
                    .frame(maxWidth: .infinity, alignment: .center)
            }

            HStack {
                // Leading Content
                if let leadingContent = leadingContent {
                    Button(action: {
                        onLeadingTap?()
                    }) {
                        leadingContent()
                            .foregroundColor(theme.actionPrimary)
                    }
                }

                Spacer()

                // Trailing Content
                if let trailingContent = trailingContent {
                    trailingContent()
                        .foregroundColor(theme.actionPrimary)
                }
            }
        }
        .padding(.spacingSM)
        .background(theme.backgroundPrimary)
        .border(sides: [.bottom], thickness: canShowBorder ? 0.5 : 0)
    }
}

// Preview example
#Preview {
    NavbarHeaderView(
        title: "Middle Title",
        leadingContent: { Image(systemName: "xmark") },
        trailingContent: { Image(systemName: "info.circle") },
        onLeadingTap: {},
        onTrailingTap: {}
    )
    NavbarHeaderView<EmptyView, EmptyView>(title: "Middle Title")
    NavbarHeaderView<EmptyView, _>(
        title: "Middle Title",
        trailingContent: { Image(systemName: "xmark") }
    )
    NavbarHeaderView<_, EmptyView>(
        title: "Middle Title",
        leadingContent: { Image(systemName: "xmark") },
        onLeadingTap: {},
        canShowBorder: true
    )
}
