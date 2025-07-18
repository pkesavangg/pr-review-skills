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
    
    /// Groups the numbers into rows of up to three so that any
    /// partially-filled final row (e.g. two items) can be centered.
    private var rows: [[Int]] {
        stride(from: 0, to: numbers.count, by: 3).map {
            Array(numbers[$0 ..< min($0 + 3, numbers.count)])
        }
    }
    
    var body: some View {
        VStack {
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
                    }

                    VStack(spacing: .spacingMD) {
                        ForEach(rows, id: \.self) { row in
                            HStack(spacing: .spacingMD) {
                                Spacer()
                                ForEach(row, id: \.self) { number in
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
                                Spacer()
                            }
                        }
                    }
                }
                .padding(.top, .spacingLG)
            }
        }
    }
}

#Preview {
    UserNumberSelectionView()
        .environmentObject(Theme.shared)
}
