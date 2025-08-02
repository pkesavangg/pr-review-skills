//
//  EditModeOverlay.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

/// A reusable overlay component that shows plus/minus circles in edit mode
/// Matches the movingGridsLearning implementation with row-based wiggle timing
struct EditModeOverlay: ViewModifier {
    let isEditMode: Bool
    let isRemoved: Bool
    let onToggleRemoval: () -> Void
    let isBeingDragged: Bool
    let isDropTarget: Bool
    let rowIndex: Int // Add row index for alternating wiggle timing
    let disableWiggle: Bool // New parameter to disable internal wiggle animation
    
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    var shouldWiggle: Bool {
        isEditMode && !isRemoved && !isBeingDragged && !disableWiggle
    }
    
    var shouldShowCircleIcon: Bool {
        isEditMode && !isBeingDragged && !isDropTarget
    }
    
    var iconOpacity: Double {
        shouldShowCircleIcon ? 1 : 0
    }

    var itemOpacity: Double {
        isRemoved ? 0.75 : 1.0
    }
    
    var dropTargetScale: CGFloat {
        isDropTarget ? 1.05 : 1.0
    }
    
    func body(content: Content) -> some View {
        ZStack(alignment: .topTrailing) {
            content
                .opacity(itemOpacity)
                .scaleEffect(dropTargetScale)
                // Apply row-based wiggle animation (matching movingGridsLearning exactly)
                .wiggling(shouldWiggle, rowIndex: rowIndex)
                .animation(.easeInOut(duration: 0.2), value: isBeingDragged)
                .animation(.easeInOut(duration: 0.2), value: isDropTarget)

            if shouldShowCircleIcon {
                let iconName = isRemoved ?
                    (themeManager.isDarkMode ? AppAssets.plusCircleDark : AppAssets.plusCircle) :
                    (themeManager.isDarkMode ? AppAssets.minusCircleDark : AppAssets.minusCircle)
                
                ThemedImage(name: iconName, isSingleMode: true)
                    .frame(width: 28, height: 28)
                    .offset(x: 5, y: -5)
                    // Apply row-based wiggle animation to icon too (matching movingGridsLearning exactly)
                    .wiggling(shouldWiggle, rowIndex: rowIndex)
                    .opacity(iconOpacity)
                    .animation(.easeInOut(duration: 0.2), value: iconOpacity)
                    .allowsHitTesting(isEditMode)
                    .zIndex(999)
                    .onTapGesture {
                        onToggleRemoval()
                    }
            }
        }
    }
}



#Preview {
    VStack(spacing: 20) {
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.blue.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Normal Item"))
            .editModeOverlay(isEditMode: false, isRemoved: false, onToggleRemoval: {})
        
        // Edit mode - not removed (should wiggle with row timing)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.green.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Edit Mode - Active (Wiggling)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {}, rowIndex: 0)
        
        // Edit mode - removed (no wiggle, plus circle)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.red.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Edit Mode - Removed (Plus Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: true, onToggleRemoval: {}, rowIndex: 1)
        
        // Edit mode - being dragged (no circle icon, still wiggles)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.orange.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Being Dragged (No Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {}, isBeingDragged: true, isDropTarget: false, rowIndex: 2)
        
        // Edit mode - drop target (no circle icon, still wiggles)
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.purple.opacity(0.2))
            .frame(width: 200, height: 100)
            .overlay(Text("Drop Target (No Circle)"))
            .editModeOverlay(isEditMode: true, isRemoved: false, onToggleRemoval: {}, isBeingDragged: false, isDropTarget: true, rowIndex: 3)
    }
    .padding()
    .environmentObject(Theme.shared)
}
