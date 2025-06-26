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
    let subtitle: String?
    let trailing: Trailing?
    let onTap: (() -> Void)?
    let rowHeight: CGFloat?

    init(
        leadingImage: Image? = nil,
        title: String,
        subtitle: String? = nil,
        trailing: Trailing? = nil,
        rowHeight: CGFloat? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.leadingImage = leadingImage
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
        self.rowHeight = rowHeight
        self.onTap = onTap
    }

    var body: some View {
        Button(action: { onTap?() }) {
            HStack(spacing: 12) {
                if let leadingImage {
                    leadingImage
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                        .foregroundColor(theme.actionPrimary)
                }
                VStack(alignment: .leading, spacing: subtitle == nil ? 0 : 2) {
                    Text(title)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    if let subtitle {
                        Text(subtitle)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                    }
                }
                Spacer()
                if let trailing {
                    trailing
                }
            }
            .frame(height: rowHeight ?? 44)
            .contentShape(Rectangle())
        }
    }
}

struct GenericListRow_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 0) {
            // Row style: Image + Title + Subtitle + Trailing Delete
            ListItemView(
                leadingImage: Image(systemName: "star"),
                title: "User Name",
                subtitle: "last active on [date]",
                trailing: Image(systemName: "trash")
                    .foregroundColor(.blue)
                    .padding(.trailing, 8),
                onTap: { print("Delete tapped") }
            )
            Divider()

            ListItemView(
                title: "Title",
                trailing: HStack(spacing: 6) {
                    Text("Detail")
                        .foregroundColor(.gray)
                        .font(.body)
                    Image(systemName: "chevron.right")
                        .foregroundColor(.blue)
                }
            )
            .overlay(Rectangle().frame(height: 1).foregroundColor(.gray.opacity(0.2)), alignment: .bottom)

            Divider()
            
            ListItemView<EmptyView>(
                title: "Short Row",
                rowHeight: 32
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
