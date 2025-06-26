//
//  ToggleListItem.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ToggleListItem: View {
    @Environment(\.appTheme) var theme
    @Binding var isOn: Bool
    let text: String?
    let image: Image?
    let showReorderHandle: Bool
    let isDisabled: Bool

    init(
        isOn: Binding<Bool>,
        text: String? = nil,
        image: Image? = nil,
        showReorderHandle: Bool = false,
        isDisabled: Bool = false
    ) {
        self._isOn = isOn
        self.text = text
        self.image = image
        self.showReorderHandle = showReorderHandle
        self.isDisabled = isDisabled
    }

    private var imageForegroundColor: Color {
        isDisabled ? theme.actionTertiaryDisabled : theme.actionPrimary
    }
    private var opacity: Double {
        isDisabled ? 0.5 : 1.0
    }
    private var shouldShowReorderHandle: Bool {
        showReorderHandle && isOn
    }

    var body: some View {
        HStack {
            if let image = image {
                image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 28, height: 28)
                    .foregroundColor(imageForegroundColor)
                    .opacity(opacity)
            }
            CustomToggleView(isOn: $isOn)
            
            if shouldShowReorderHandle {
                
                Divider()
                    .foregroundColor(theme.statusUtility)
                
                Image(systemName: "line.3.horizontal")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundColor(isDisabled ? theme.actionTertiaryDisabled : theme.statusIconSecondary)
                    .fontWeight(.semibold)
                    .padding(.vertical, 11)
                    .padding(.horizontal, 13.5)
                    .opacity(opacity)
            }
        }
        .frame(height: 48)
    }
}

import SwiftUI

struct ToggleListItem_Previews: PreviewProvider {
    struct ReorderCaseDemo: View {
        @State var isOn = false

        var body: some View {
            VStack(spacing: 8) {
                Text("Reorder Handle only shows when toggle is ON")
                    .font(.caption)
                    .foregroundColor(.gray)
                ToggleListItem(
                    isOn: $isOn,
                    text: "Reorder Example",
                    image: Image(systemName: "arrow.up.arrow.down.square"),
                    showReorderHandle: true,
                    isDisabled: false
                )
                .padding(.bottom, 2)
                Text("Toggle is \(isOn ? "ON (handle shows)" : "OFF (handle hidden)")")
                    .font(.caption2)
            }
            .padding()
        }
    }

    static var previews: some View {
        VStack(spacing: 20) {
            // Only text
            ToggleListItem(
                isOn: .constant(true),
                text: "Wi-Fi"
            )

            // Text + image
            ToggleListItem(
                isOn: .constant(false),
                text: "Bluetooth",
                image: Image(systemName: "bolt.horizontal.fill")
            )

            // Only image
            ToggleListItem(
                isOn: .constant(true),
                image: Image(systemName: "heart.fill")
            )

            // With reorder handle (will only show when toggle is ON)
            ReorderCaseDemo()

            // Disabled
            ToggleListItem(
                isOn: .constant(false),
                text: "Location",
                image: Image(systemName: "location.fill"),
                isDisabled: true
            )

            // Disabled with reorder handle (won't show since toggle is OFF)
            ToggleListItem(
                isOn: .constant(false),
                text: "Airplane Mode",
                image: Image(systemName: "airplane"),
                showReorderHandle: true,
                isDisabled: true
            )

            // Custom icon example (star)
            ToggleListItem(
                isOn: .constant(true),
                text: "Premium",
                image: Image(systemName: "star.fill")
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
