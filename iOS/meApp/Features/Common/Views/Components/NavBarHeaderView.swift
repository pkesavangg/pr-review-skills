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
    var titleColor: Color?
    var leadingContent: (() -> Leading)?
    var trailingContent: (() -> Trailing)?
    var onLeadingTap: (() -> Void)?
    var onTrailingTap: (() -> Void)?
    var onTitleTap: (() -> Void)?
    var canShowBorder = false
    var canShowTitleChevron = false
    var canShowPresentationIndicator = false
    var shouldShowBackground: Bool = true
    /// Optional accessibility identifier applied to the leading button this header builds.
    /// Lets callers expose a stable automation hook (e.g. a Close button) on the leaf control.
    var leadingAccessibilityID: String?

    var body: some View {
        ZStack {
            // Center Title
            if let title = title {
                HStack(spacing: 4) {
                    Spacer()
                    Text(title)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(titleColor ?? theme.textHeading)
                        .lineLimit(1)
                        .accessibilityAddTraits(.isHeader)
                    if canShowTitleChevron {
                        Image(systemName: "chevron.down") // Placeholder — replace with asset icon when available
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)
                    }
                    Spacer()
                }
                .onTapGesture {
                    onTitleTap?()
                }
            }

            HStack {
                // Leading Content
                if let leadingContent = leadingContent {
                    Button(action: {
                        onLeadingTap?()
                    }, label: {
                        leadingContent()
                            .foregroundColor(theme.actionPrimary)
                    })
                    .accessibilityIdentifier(leadingAccessibilityID ?? "")
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
                        .padding(.top, 4) // Placeholder value until UX finalizes top padding.
                    
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
