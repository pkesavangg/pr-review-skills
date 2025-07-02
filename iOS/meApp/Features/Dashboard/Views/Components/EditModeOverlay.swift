//
//  EditModeOverlay.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

/// A reusable overlay component that shows plus/minus circles in edit mode
struct EditModeOverlay: ViewModifier {
    let isEditMode: Bool
    let isRemoved: Bool
    let onToggleRemoval: () -> Void
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    var shouldWiggle: Bool {
        isEditMode && !isRemoved
    }
    
    func body(content: Content) -> some View {
        ZStack(alignment: .topTrailing) {
            content
                .opacity(isRemoved ? 0.75 : 1.0)
                .wiggling(shouldWiggle)
            
            // Edit mode plus/minus circle
            if isEditMode {
                let iconName = isRemoved ?
                (themeManager.isDarkMode ? AppAssets.plusCircleDark : AppAssets.plusCircle) :
                (themeManager.isDarkMode ? AppAssets.minusCircleDark : AppAssets.minusCircle)
                
                ThemedImage(name: iconName, isSingleMode: true)
                    .frame(width: 28, height: 28)
                    .offset(x: 5, y: -5)
                    .wiggling(shouldWiggle)
                    .onTapGesture {
                        onToggleRemoval()
                    }
            }
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        // Normal state
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.blue.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Normal Item"))
            .editModeOverlay(isEditMode: false, isRemoved: false, onToggleRemoval: {})
        
        // Edit mode - not removed (should wiggle)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.green.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Edit Mode - Active (Wiggling)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {})
        
        // Edit mode - removed (no wiggle)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.red.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Edit Mode - Removed (No Wiggle)"))
            .editModeOverlay(isEditMode: true, isRemoved: true, onToggleRemoval: {})
    }
    .padding()
    .environmentObject(Theme.shared)
}
