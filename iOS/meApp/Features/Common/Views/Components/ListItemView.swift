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
    let leadingImage: Image?
    let title: String
    let subtitleTop: String?
    let subtitleBottom: String?
    let trailing: Trailing?
    let onTap: (() -> Void)?
    let rowHeight: CGFloat?
    
    init(
        leadingImage: Image? = nil,
        title: String,
        subtitleTop: String? = nil,
        subtitleBottom: String? = nil,
        trailing: Trailing? = nil,
        rowHeight: CGFloat? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.leadingImage = leadingImage
        self.title = title
        self.subtitleTop = subtitleTop
        self.subtitleBottom = subtitleBottom
        self.trailing = trailing
        self.rowHeight = rowHeight
        self.onTap = onTap
    }
    init(
        leadingImage: Image? = nil,
        title: String,
        subtitle: String? = nil,
        trailing: Trailing? = nil,
        rowHeight: CGFloat? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.init(leadingImage: leadingImage, title: title, subtitleTop: nil, subtitleBottom: subtitle, trailing: trailing, rowHeight: rowHeight, onTap: onTap)
    }
    
    var body: some View {
        HStack(spacing: 12) {
            if let leadingImage {
                leadingImage
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
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
        .padding(.vertical, .spacingSM)
        .padding(.horizontal,.spacingSM)
        .background(theme.backgroundPrimary)
        .frame(height: rowHeight ?? 44)
    }
}

struct GenericListRow_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 0) {
            ListItemView<EmptyView>(
                title: "Title"
            )
            ListItemView(
                title: "Title With Trailing",
                trailing: Image(systemName: "chevron.right")
            )
        }
        .frame(maxHeight: .infinity)
        .background(Color.gray)
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
