//
//  PassThroughWindow.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import Foundation
import SwiftUI

// MARK: - PassThroughWindow
/// A custom UIWindow that conditionally allows touch events to pass through
/// based on whether a modal is currently being presented.
class PassThroughWindow: UIWindow {
    @Injector var notificationHelperService: NotificationHelperService
    
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        // Get view from superclass
        guard let hitView = super.hitTest(point, with: event) else { return nil }
        
        // Modal overlays (alerts, loaders, modals) - block everything
        if notificationHelperService.isOverlayActive {
            return hitView
        }

        if notificationHelperService.isToastVisible {
            let toastArea = CGRect(x: 0, y: 0, width: bounds.width, height: bounds.height * 0.3)
            if toastArea.contains(point) {
                return hitView
            } else {
                return nil
            }
        }
        
        // No overlays: pass through unless we hit actual UI
        let shouldCapture = hitView != rootViewController?.view
        return shouldCapture ? hitView : nil
    }
}
