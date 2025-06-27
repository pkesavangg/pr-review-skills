//
//  ListItemView.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

/// A generic row for lists, supporting optional leading icon, subtitle, and custom trailing/action content.
/// Use for both "User Name + Delete" and "Title + Detail + Chevron" row styles.
struct ListItemView<Trailing: View>: View {
    @Environment(\.appTheme) private var theme
    let leadingImage: String?
    let title: String
    let subtitleTop: String?
    let subtitleBottom: String?
    let trailing: Trailing?
    let onTap: (() -> Void)?
    let rowHeight: CGFloat?
    let verticalPadding: CGFloat
    
    init(
        leadingImage: String? = nil,
        title: String,
        subtitleTop: String? = nil,
        subtitleBottom: String? = nil,
        trailing: Trailing? = nil,
        rowHeight: CGFloat? = nil,
        onTap: (() -> Void)? = nil,
        verticalPadding: CGFloat = .spacingSM
    ) {
        self.leadingImage = leadingImage
        self.title = title
        self.subtitleTop = subtitleTop
        self.subtitleBottom = subtitleBottom
        self.trailing = trailing
        self.rowHeight = rowHeight
        self.onTap = onTap
        self.verticalPadding = verticalPadding
    }
    init(
        leadingImage: String? = nil,
        title: String,
        subtitle: String? = nil,
        trailing: Trailing? = nil,
        rowHeight: CGFloat? = nil,
        onTap: (() -> Void)? = nil,
        verticalPadding: CGFloat = .spacingSM
    ) {
        self.init(
            leadingImage: leadingImage,
            title: title,
            subtitleTop: nil,
            subtitleBottom: subtitle,
            trailing: trailing,
            rowHeight: rowHeight,
            onTap: onTap,
            verticalPadding: verticalPadding
        )
    }
    
    var body: some View {
        HStack(spacing: 12) {
            if let leadingImage {
                AppIconView(icon: leadingImage)
                    .foregroundColor(theme.actionPrimary)
            }
            VStack(alignment: .leading, spacing: 2) {
                if let subtitleTop {
                    Text(subtitleTop)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
                Text(title)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                if let subtitleBottom {
                    Text(subtitleBottom)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
            }
            Spacer()
            if let trailing {
                trailing
                    .onTapGesture(perform: {onTap?()})
            }
        }
        .padding(.vertical, verticalPadding)
        .padding(.horizontal, .spacingSM)
        .background(theme.backgroundPrimary)
        .frame(height: rowHeight ?? 44)
    }
}

struct GenericListRow_Previews: PreviewProvider {
    static var previews: some View {
        VStack() {
            ListItemView<EmptyView>(
                title: "Title"
            )
            ListItemView(
                title: "Title With Trailing",
                trailing: Image(systemName: "chevron.right")
            )
            
            ListItemView(
                leadingImage: AppAssets.filledTickCircle,
                title: "Title With Trailing",
                trailing: Image(systemName: "chevron.right")
            )
            Divider()
            ListItemView(
                leadingImage: AppAssets.filledTickCircle,
                title: "Title With Trailing",
                trailing: Image(systemName: "chevron.right")
            )
            Divider()
            ListItemView<EmptyView>(
                leadingImage: AppAssets.filledTickCircle,
                title: "Title With Trailing"
            )
            Divider()
            ListItemView<EmptyView>(
                leadingImage: AppAssets.filledTickCircle,
                title: "Title With Trailing"
            )
        }
        .frame(maxHeight: .infinity)
        .background(Color.gray)
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
