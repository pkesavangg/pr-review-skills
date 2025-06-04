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
        // Get view from superclass.
        guard let hitView = super.hitTest(point, with: event) else { return nil }
        // If the returned view is the `UIHostingController`'s view, ignore.
        
        // If the root view controller's view is the hit view, check if a overlay is active.
        return rootViewController?.view == hitView ? notificationHelperService.isOverlayActive ? hitView : nil : hitView
    }
}
