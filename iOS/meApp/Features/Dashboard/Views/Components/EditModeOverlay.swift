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
    let isBeingDragged: Bool
    let isDropTarget: Bool
    
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    var shouldWiggle: Bool {
        isEditMode && !isRemoved
    }
    
    var shouldShowCircleIcon: Bool {
        // Show circle icon if:
        // 1. We're in edit mode
        // 2. The item is not currently being dragged (more stable check)
        // 3. The item is not currently a drop target
        isEditMode && !isBeingDragged && !isDropTarget
    }
    
    var iconOpacity: Double {
        // Use a more stable opacity calculation to prevent flickering
        if !isEditMode {
            return 0
        }
        
        if isBeingDragged || isDropTarget {
            return 0
        }
        
        return 1
    }
    
    func body(content: Content) -> some View {
        ZStack(alignment: .topTrailing) {
            content
                .opacity(isRemoved ? 0.75 : 1.0)
                .wiggling(shouldWiggle)
            
            // Always render the icon, but fade it in/out
            let iconName = isRemoved ?
                (themeManager.isDarkMode ? AppAssets.plusCircleDark : AppAssets.plusCircle) :
                (themeManager.isDarkMode ? AppAssets.minusCircleDark : AppAssets.minusCircle)
            ThemedImage(name: iconName, isSingleMode: true)
                .frame(width: 28, height: 28)
                .offset(x: 5, y: -5)
                .wiggling(shouldWiggle)
                .opacity(iconOpacity)
                .animation(.easeInOut(duration: 0.2), value: iconOpacity)
                .allowsHitTesting(shouldShowCircleIcon)
                .onTapGesture {
                    onToggleRemoval()
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
        
        // Edit mode - removed (no wiggle, plus circle)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.red.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Edit Mode - Removed (Plus Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: true, onToggleRemoval: {})
        
        // Edit mode - being dragged (no circle icon, still wiggles)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.orange.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Being Dragged (No Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {}, isBeingDragged: true, isDropTarget: false)
        
        // Edit mode - drop target (no circle icon, still wiggles)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.purple.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Drop Target (No Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {}, isBeingDragged: false, isDropTarget: true)
        
        // Edit mode - removed and being dragged (no circle, no wiggle)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.gray.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Removed & Dragged (No Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: true, onToggleRemoval: {}, isBeingDragged: true, isDropTarget: false)
    }
    .padding()
    .environmentObject(Theme.shared)
}
