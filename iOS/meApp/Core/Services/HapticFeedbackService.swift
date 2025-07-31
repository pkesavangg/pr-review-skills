//
//  HapticFeedbackService.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import UIKit

/// Service for providing haptic feedback throughout the app
/// Follows the exact pattern from movingGridsLearning
struct HapticFeedbackService {
    
    // MARK: - Haptic Feedback Methods
    
    /// Provides light impact feedback
    static func light() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
    }
    
    /// Provides medium impact feedback
    static func medium() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
    
    /// Provides heavy impact feedback
    static func heavy() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .heavy)
        impactFeedback.impactOccurred()
    }
    
    /// Provides success notification feedback
    static func success() {
        let notificationFeedback = UINotificationFeedbackGenerator()
        notificationFeedback.notificationOccurred(.success)
    }
    
    /// Provides warning notification feedback
    static func warning() {
        let notificationFeedback = UINotificationFeedbackGenerator()
        notificationFeedback.notificationOccurred(.warning)
    }
    
    /// Provides error notification feedback
    static func error() {
        let notificationFeedback = UINotificationFeedbackGenerator()
        notificationFeedback.notificationOccurred(.error)
    }
} 