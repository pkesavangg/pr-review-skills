//
//  SignupStepWrapper.swift
//  meApp
//
//  Created by AI Assistant on 14/06/25.
//

import SwiftUI

/// A reusable wrapper for Signup step screens that provides a common
/// scrollable layout with a standardized header (title and subtitle).
struct SignupStepWrapper<Content: View>: View {
    @Environment(\.appTheme) private var theme
    let title: String
    let subtitle: String
    @ViewBuilder let content: Content

    init(title: String, subtitle: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.content = content()
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .accessibilityAddTraits(.isHeader)

                    Text(subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textSubheading)
                }

                VStack(alignment: .leading, spacing: 0) {
                    content
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

#Preview {
    SignupStepWrapper(title: "Title", subtitle: "Subtitle") {
        Text("Content")
            .padding(.top, .spacingLG)
    }
    .padding()
}
