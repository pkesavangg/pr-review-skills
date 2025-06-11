import Foundation
import SwiftUI

/// Drop shadow tokens for consistent shadow effects
public enum DropShadow {
    /// Glow White: Offset (0,0), Blur 16, Color #FFFFFF @ 25%
    public static let glowWhite = ShadowStyle(
        color: Color.white.opacity(0.25),
        radius: BorderRadius.lg,
        x: 0,
        y: 0
    )
    /// Glow Black: Offset (0,0), Blur 16, Color #000000 @ 25%
    public static let glowBlack = ShadowStyle(
        color: Color.black.opacity(0.25),
        radius: BorderRadius.lg,
        x: 0,
        y: 0
    )
    /// Shadow: Offset (0,4), Blur 8, Color #000000 @ 25%
    public static let shadow = ShadowStyle(
        color: Color.black.opacity(0.25),
        radius: BorderRadius.sm,
        x: 0,
        y: 4
    )
    /// Layer Blur: Blur 4px (applied as a background blur)
    public static let blurRadius: CGFloat = BorderRadius.sm
}

/// Helper struct for shadow style
public struct ShadowStyle {
    public let color: Color
    public let radius: CGFloat
    public let x: CGFloat
    public let y: CGFloat

    public init(color: Color, radius: CGFloat, x: CGFloat, y: CGFloat) {
        self.color = color
        self.radius = radius
        self.x = x
        self.y = y
    }
}

// MARK: - SwiftUI View Extension for DropShadow
public extension View {
    /// Apply a DropShadow token style
    func dropShadow(_ style: ShadowStyle) -> some View {
        self.shadow(color: style.color, radius: style.radius, x: style.x, y: style.y)
    }
    /// Apply a layer blur (background blur)
    func layerBlur() -> some View {
        self.blur(radius: DropShadow.blurRadius)
    }
}

// MARK: - Usage Guide
/*
 How to use drop shadow tokens in your SwiftUI views:

 1. Using DropShadow enum directly:
    ```
    RoundedRectangle(cornerRadius: .radiusMD)
        .fill(Color.pink)
        .dropShadow(DropShadow.glowWhite)

    RoundedRectangle(cornerRadius: .radiusMD)
        .fill(Color.pink)
        .dropShadow(DropShadow.glowBlack)

    RoundedRectangle(cornerRadius: .radiusMD)
        .fill(Color.pink)
        .dropShadow(DropShadow.shadow)

    // Layer blur
    RoundedRectangle(cornerRadius: .radiusMD)
        .fill(Color.pink)
        .layerBlur()
    ```

 2. Custom shadow:
    ```
    RoundedRectangle(cornerRadius: .radiusMD)
        .fill(Color.pink)
        .shadow(color: Color.black.opacity(0.25), radius: 8, x: 0, y: 4)
    ```
*/