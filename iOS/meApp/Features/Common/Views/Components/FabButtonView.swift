//
//  FabButtonView.swift
//  meApp
//
//  Created by AI Assistant on 17/01/25.
//

import SwiftUI

// MARK: - FAB Button View
/// A floating action button that displays a customizable icon with theme-based styling.
///
/// Features:
/// - Generic icon support - can display any image from the app's assets
/// - Theme-aware colors and shadows
/// - Configurable size (standard FAB size by default)
/// - Smooth tap animation with haptic feedback
/// - Accessibility support
///
/// Usage:
/// ```swift
/// FabButtonView(icon: AppAssets.plus) {
///     // Handle tap action
/// }
/// ```
struct FabButtonView: View {
    @Environment(\.appTheme) private var theme

    let icon: String
    let size: CGFloat
    let iconSize: IconSize
    let backgroundColor: Color?
    let iconColor: Color?
    let action: () -> Void

    @State private var isPressed = false

    init(
        icon: String,
        size: CGFloat = 56,
        iconSize: IconSize = IconSize(width: 24, height: 24),
        backgroundColor: Color? = nil,
        iconColor: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.icon = icon
        self.size = size
        self.iconSize = iconSize
        self.backgroundColor = backgroundColor
        self.iconColor = iconColor
        self.action = action
    }

    var body: some View {
        Button(action: {
            // Add haptic feedback
            let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
            impactFeedback.impactOccurred()
            action()
        }) {
            AppIconView(icon: icon, size: iconSize)
                .foregroundColor(iconColor ?? theme.backgroundPrimary)
        }
        .frame(width: size, height: size)
        .background(backgroundColor ?? theme.actionPrimary)
        .clipShape(Circle())
        .dropShadow(DropShadow.glowBlack)
        .scaleEffect(isPressed ? 0.95 : 1.0)
        .animation(.easeInOut(duration: 0.1), value: isPressed)
        .accessibilityLabel("Floating action button")
    }
}

// MARK: - Preview
#Preview {
    VStack(spacing: 40) {
        // Standard FAB with plus icon
        FabButtonView(icon: AppAssets.weightOnlyMode) {
            print("FAB tapped")
        }

        // FAB with custom size and icon
        FabButtonView(
            icon: AppAssets.xmark,
            size: 48,
            iconSize: IconSize(width: 20, height: 20)
        ) {
            print("Close FAB tapped")
        }

        // FAB with custom colors
        FabButtonView(
            icon: AppAssets.helpCircle,
            backgroundColor: .green,
            iconColor: .white
        ) {
            print("Help FAB tapped")
        }

        // Small FAB
        FabButtonView(
            icon: AppAssets.chevronUp,
            size: 40,
            iconSize: IconSize(width: 16, height: 16)
        ) {
            print("Small FAB tapped")
        }
    }
    .padding()
    .background(Color.gray.opacity(0.1))
    .environmentObject(Theme.shared)
}
