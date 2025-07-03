//
//  ScaleInfoStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import SwiftUI

/// Generic instruction view used to present a heading + body copy in AppSync setup flow.
struct ScaleInstructionView: View {
    @Environment(\.appTheme) private var theme

    let title: String
    let description: String

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            HStack {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Text(description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                Spacer()
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ScaleInstructionView(title: "Sample title", description: "Sample description")
        .environmentObject(Theme.shared)
} 
