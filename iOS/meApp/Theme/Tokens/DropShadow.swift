import Foundation
import SwiftUI

/// Drop shadow tokens for consistent shadow effects
public enum DropShadow {
    /// Shadow: Offset (0,4), Blur 8, uses theme.glow color
    public static func shadow(color: Color) -> ShadowStyle {
        ShadowStyle(
            color: color,
            radius: BorderRadius.sm,
            x: 0,
            y: 4
        )
    }
    
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
    
    /// Apply theme-based shadow using theme.glow color
    func themeShadow() -> some View {
        self.modifier(ThemeShadowModifier())
    }
    
    /// Apply theme-based glow shadow using theme.glow color (legacy - use themeShadow instead)
    func themeDropShadow() -> some View {
        self.modifier(ThemeGlowModifier())
    }
    
    /// Apply a layer blur (background blur)
    func layerBlur() -> some View {
        self.blur(radius: DropShadow.blurRadius)
    }
}

// MARK: - Theme-aware Shadow Modifiers
struct ThemeShadowModifier: ViewModifier {
    @Environment(\.appTheme) var theme
    
    func body(content: Content) -> some View {
        let shadowStyle = DropShadow.shadow(color: theme.glow)
        return content.shadow(
            color: shadowStyle.color,
            radius: shadowStyle.radius,
            x: shadowStyle.x,
            y: shadowStyle.y
        )
    }
}

struct ThemeGlowModifier: ViewModifier {
    @Environment(\.appTheme) var theme
    
    func body(content: Content) -> some View {
        content.shadow(
            color: theme.glow,
            radius: BorderRadius.xs,
            x: 2,
            y: 2
        )
    }
}

// MARK: - Usage Guide
/*
 How to use drop shadow tokens in your SwiftUI views:
 
 1. Using theme-based shadow (recommended):
 ```
 RoundedRectangle(cornerRadius: .radiusMD)
 .fill(Color.pink)
 .themeShadow()
 ```
 
 2. Using DropShadow with theme color:
 ```
 @Environment(\.appTheme) var theme
 
 RoundedRectangle(cornerRadius: .radiusMD)
 .fill(Color.pink)
 .dropShadow(DropShadow.shadow(color: theme.glow))
 ```
 
 3. Using theme-based glow (legacy):
 ```
 RoundedRectangle(cornerRadius: .radiusMD)
 .fill(Color.pink)
 .themeDropShadow()
 ```
 
 4. Layer blur:
 ```
 RoundedRectangle(cornerRadius: .radiusMD)
 .fill(Color.pink)
 .layerBlur()
 ```
 
 5. Custom shadow with specific color:
 ```
 RoundedRectangle(cornerRadius: .radiusMD)
 .fill(Color.pink)
 .dropShadow(DropShadow.shadow(color: Color.red.opacity(0.3)))
 ```
 */
