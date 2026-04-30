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
    var onTitleTap: (() -> Void)?
    var canShowBorder = false
    var canShowPresentationIndicator = false
    var shouldShowBackground: Bool = true
    
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
                    .onTapGesture {
                        onTitleTap?()
                    }
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
        .padding(.horizontal, .spacingSM)
        .frame(height: 56)
        .background(shouldShowBackground ? theme.backgroundPrimary : Color.clear)
        .border(sides: [.bottom], thickness: canShowBorder ? 0.5 : 0)
        .overlay {
            if canShowPresentationIndicator {
                VStack(spacing: 0) {
                    Capsule()
                        .fill(theme.statusUtilityPrimary)
                        .frame(width: 36, height: 5)
                        .padding(.top, 4) // TODO: Need to update after UX design provides the correct padding
                    
                    Spacer()
                }
            }
        }
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
        trailingContent: { Image(systemName: "xmark") },
        canShowPresentationIndicator: true
    )
    NavbarHeaderView<_, EmptyView>(
        title: "Middle Title",
        leadingContent: { Image(systemName: "xmark") },
        onLeadingTap: {},
        canShowBorder: true
    )
}
