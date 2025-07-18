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
    @State private var selectedNumber: Int? = nil

    /// Callback triggered when a user number is tapped.
    let onNumberSelected: ((Int) -> Void)? = nil

    /// All available user numbers (U1–U8)
    private let numbers = Array(1...8)
    
    /// Three-column grid for the circle layout
    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: .spacingLG), count: 3)
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                HStack {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.actionPrimary)

                        Text(lang.description)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    Spacer()
                }

                // User number grid
                LazyVGrid(columns: columns, spacing: .spacingMD) {
                    ForEach(numbers, id: \.self) { number in
                        let isSelected = number == selectedNumber
                        Button(action: {
                            selectedNumber = number
                            onNumberSelected?(number)
                        }) {
                            ZStack {
                                Circle()
                                    .fill(isSelected ? theme.actionPrimary : Color.clear)
                                    .frame(width: 100, height: 100)
                                    .overlay(
                                        Circle()
                                            .stroke(theme.actionPrimary, lineWidth: 2)
                                    )

                                Text("U\(number)")
                                    .fontOpenSans(.button1)
                                    .foregroundColor(isSelected ? theme.actionInverse : theme.actionPrimary)
                            }
                        }
                    }
                }
                .padding(.horizontal, .spacingSM)
            }
            .padding(.top, .spacingLG)
            .padding(.horizontal, .spacingSM)
        }
    }
}

#Preview {
    UserNumberSelectionView()
        .environmentObject(Theme.shared)
}
