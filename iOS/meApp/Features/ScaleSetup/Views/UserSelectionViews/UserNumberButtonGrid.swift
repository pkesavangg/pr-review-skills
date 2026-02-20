//
//  UserNumberButtonGrid.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/07/25.
//

import SwiftUI

/// A grid of circular buttons displaying user numbers
struct UserNumberButtonGrid: View {
    @Environment(\.appTheme) private var theme
    
    let numbers: [Int]
    @Binding var selectedNumber: Int?
    let onNumberSelected: ((Int) -> Void)?
    
    /// Groups the numbers into rows of up to three so that any
    /// partially-filled final row (e.g. two items) can be centered.
    private var rows: [[Int]] {
        stride(from: 0, to: numbers.count, by: 3).map {
            Array(numbers[$0 ..< min($0 + 3, numbers.count)])
        }
    }
    
    var body: some View {
        VStack(spacing: .spacingMD) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: .spacingMD) {
                    ForEach(row, id: \.self) { number in
                        let isSelected = number == selectedNumber
                        ZStack {
                            Circle()
                                .fill(isSelected ? theme.actionPrimary : Color.clear)
                                .overlay(
                                    Circle()
                                        .stroke(theme.actionPrimary, lineWidth: 2)
                                )
                            
                            Text("U\(number)")
                                .fontOpenSans(.button1)
                                .foregroundColor(isSelected ? theme.actionInverse : theme.actionPrimary)
                        }
                        .frame(width: 100, height: 100)
                        .contentShape(Circle())
                        .onTapGesture {
                            withAnimation {
                                selectedNumber = number
                                onNumberSelected?(number)
                            }
                        }
                    }
                }
            }
        }
    }
}

#Preview("Button Grid Only") {
    UserNumberButtonGrid(
        numbers: Array(1...8),
        selectedNumber: .constant(3)
    )        { number in
            print("Selected U\(number)")
        }
    .environmentObject(Theme.shared)
    .padding()
}
