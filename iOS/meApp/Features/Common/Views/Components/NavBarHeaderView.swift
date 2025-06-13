//
//  NavBarHeaderView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

struct NavbarHeaderView<Leading: View, Trailing: View>: View {
    @Environment(\.appTheme) var theme
    
    let title: String
    var leadingContent: (() -> Leading)?
    var trailingContent: (() -> Trailing)?
    var onLeadingTap: (() -> Void)?
    var onTrailingTap: (() -> Void)?
    
    var body: some View {
        HStack (alignment: .center){
            if let leadingContent = leadingContent {
                Button(action: {
                    onLeadingTap?()
                }) {
                    leadingContent()
                        .foregroundColor(theme.actionPrimary)
                }
            }
            
            Spacer()
            
            Text(title)
                .fontOpenSans(.heading5)
                .fontWeight(.bold)
                .foregroundColor(theme.actionSecondary)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
                .padding(.leading, leadingContent == nil ? 30 : 0)
                .padding(.trailing, trailingContent == nil ? 30 : 0)
            
            Spacer()
            
            if let trailingContent = trailingContent {
                Button(action: {
                    onTrailingTap?()
                }) {
                    trailingContent()
                        .foregroundColor(theme.actionPrimary)
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
    NavbarHeaderView<EmptyView, EmptyView>(title: "Title Only")
    NavbarHeaderView<EmptyView, _>(
        title: "Trailing Only",
        trailingContent: { Image(systemName: "xmark") }
    )
    NavbarHeaderView<_, EmptyView>(
        title: "Leading Only",
        leadingContent: { Image(systemName: "xmark") },
        onLeadingTap: {}
    )
}
