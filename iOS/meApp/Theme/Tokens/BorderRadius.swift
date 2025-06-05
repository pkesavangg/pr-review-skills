import Foundation
import SwiftUI

/// Border radius tokens for consistent corner rounding
public enum BorderRadius {
    /// Extra small radius (4px)
    public static let xs: CGFloat = 4
    
    /// Small radius (8px)
    public static let sm: CGFloat = 8
    
    /// Medium radius (12px)
    public static let md: CGFloat = 12
    
    /// Large radius (16px)
    public static let lg: CGFloat = 16
    
    /// Extra large radius (28px)
    public static let xl: CGFloat = 28
    
    /// 2x Extra large radius (44px)
    public static let xxl: CGFloat = 44
    
    /// Pill/Capsule radius (999px)
    public static let pill: CGFloat = 999
}

// MARK: - Convenience Extensions
public extension CGFloat {
    /// Extra small radius (4)
    static let radiusXS = BorderRadius.xs
    
    /// Small radius (8)
    static let radiusSM = BorderRadius.sm
    
    /// Medium radius (12)
    static let radiusMD = BorderRadius.md
    
    /// Large radius (16)
    static let radiusLG = BorderRadius.lg
    
    /// Extra large radius (28)
    static let radiusXL = BorderRadius.xl
    
    /// 2x Extra large radius (44)
    static let radius2XL = BorderRadius.xxl
    
    /// Pill/Capsule radius (999)
    static let radiusPill = BorderRadius.pill
}

// MARK: - Usage Guide
/*
 How to use border radius tokens in your SwiftUI views:
 
 1. Using BorderRadius enum directly:
    ```
    RoundedRectangle(cornerRadius: BorderRadius.md)
    
    Rectangle()
        .cornerRadius(BorderRadius.lg)
    ```
 
 2. Using CGFloat extension (recommended):
    ```
    RoundedRectangle(cornerRadius: .radiusMD)
    
    Rectangle()
        .cornerRadius(.radiusLG)
    ```
 
 3. Common use cases:
    ```
    // Basic button
    Button(action: {}) {
        Text("Click Me")
            .padding()
            .background(Color.blue)
            .cornerRadius(.radiusSM)
    }
    
    // Card view
    VStack {
        content
    }
    .background(Color.white)
    .cornerRadius(.radiusLG)
    .shadow(radius: 4)
    
    // Pill button
    Button(action: {}) {
        Text("Subscribe")
            .padding(.horizontal, .spacingMD)
            .padding(.vertical, .spacingSM)
            .background(Color.blue)
            .cornerRadius(.radiusPill)
    }
    ```
 */ 