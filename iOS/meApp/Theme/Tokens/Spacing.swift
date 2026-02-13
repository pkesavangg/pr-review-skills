import Foundation

/// Spacing tokens following 8-point grid system
public enum Spacing {
    /// Extra small spacing (8px)
    public static let xs: CGFloat = 8
    
    /// Extra small medium spacing (12px)
    public static let xsm: CGFloat = 12
    
    /// Small spacing (16px)
    public static let sm: CGFloat = 16
    
    /// Medium spacing (24px)
    public static let md: CGFloat = 24
    
    /// Large spacing (32px)
    public static let lg: CGFloat = 32
    
    /// Extra large spacing (40px)
    public static let xl: CGFloat = 40
    
    /// 2x Extra large spacing (48px)
    public static let xxl: CGFloat = 48
    
    /// 3x Extra large spacing (56px)
    public static let xxxl: CGFloat = 56
    
    /// 4x Extra large spacing (64px)
    public static let xxxxl: CGFloat = 64
    
    /// 5x Extra large spacing (72px)
    public static let xxxxxl: CGFloat = 72
    
    /// 6x Extra large spacing (80px)
    public static let xxxxxxl: CGFloat = 80
}

// MARK: - Convenience Extensions
public extension CGFloat {
    /// Extra small spacing (8)
    static let spacingXS = Spacing.xs
    
    /// Extra small medium spacing (12)
    static let spacingXSM = Spacing.xsm
    
    /// Small spacing (16)
    static let spacingSM = Spacing.sm
    
    /// Medium spacing (24)
    static let spacingMD = Spacing.md
    
    /// Large spacing (32)
    static let spacingLG = Spacing.lg
    
    /// Extra large spacing (40)
    static let spacingXL = Spacing.xl
    
    /// 2x Extra large spacing (48)
    static let spacing2XL = Spacing.xxl
    
    /// 3x Extra large spacing (56)
    static let spacing3XL = Spacing.xxxl
    
    /// 4x Extra large spacing (64)
    static let spacing4XL = Spacing.xxxxl
    
    /// 5x Extra large spacing (72)
    static let spacing5XL = Spacing.xxxxxl
    
    /// 6x Extra large spacing (80)
    static let spacing6XL = Spacing.xxxxxxl
}

// MARK: - Usage Guide
/*
 How to use spacing tokens in your SwiftUI views:
 
 1. Using Spacing enum directly:
    ```
    VStack(spacing: Spacing.md) {
        Text("Hello")
        Text("World")
    }
    
    HStack {
        Text("Left")
            .padding(.trailing, Spacing.sm)
        Text("Right")
    }
    ```
 
 2. Using CGFloat extension (recommended):
    ```
    VStack(spacing: .spacingMD) {
        Text("Hello")
        Text("World")
    }
    
    Text("Padded Text")
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingLG)
    ```
 
 3. Common use cases:
    ```
    // List spacing
    List {
        ForEach(items) { item in
            ItemRow()
                .padding(.vertical, .spacingXS)
        }
    }
    
    // Button spacing
    Button(action: {}) {
        HStack(spacing: .spacingSM) {
            Image(systemName: "plus")
            Text("Add Item")
        }
        .padding(.horizontal, .spacingMD)
    }
    
    // Grid spacing
    LazyVGrid(
        columns: columns,
        spacing: .spacingLG
    ) {
        // Grid items
    }
    ```
 
 4. Responsive spacing:
    ```
    // Conditional spacing based on size class
    @Environment(\.horizontalSizeClass) var sizeClass
    
    let padding: CGFloat = sizeClass == .compact ? .spacingSM : .spacingLG
    
    VStack {
        Content()
    }
    .padding(padding)
    ```
 */ 