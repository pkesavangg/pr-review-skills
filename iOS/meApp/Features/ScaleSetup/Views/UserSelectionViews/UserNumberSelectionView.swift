//
//  UserNumberSelectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/07/25.
//

import SwiftUI

struct UserNumberSelectionView: View {
    @Environment(\.appTheme) private var theme

    private let lang = ScaleSetupStrings.UserNumberSelectionViewStrings.self
    @State var selectedNumber: Int?

    /// Callback triggered when a user number is tapped.
    var onNumberSelected: ((Int) -> Void)?

    /// All available user numbers (U1–U8)
    private let numbers = Array(1...8)
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .center, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)

                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                }
                .padding(.horizontal, .spacingSM)
                .frame(maxWidth: .infinity, alignment: .leading)
                UserNumberButtonGrid(
                    numbers: numbers,
                    selectedNumber: $selectedNumber,
                    onNumberSelected: onNumberSelected
                )
                .padding(.horizontal, 2)
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    UserNumberSelectionView { _ in }
        .environmentObject(Theme.shared)
}
