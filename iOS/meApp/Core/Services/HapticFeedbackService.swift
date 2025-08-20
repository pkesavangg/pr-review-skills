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
    
    // MARK: - Cooldown (to prevent rapid repeated vibrations)
    private static var lastLightAt: CFTimeInterval = 0
    private static var lastMediumAt: CFTimeInterval = 0
    private static var lastHeavyAt: CFTimeInterval = 0
    private static let defaultLightInterval: CFTimeInterval = 0.6
    private static let defaultMediumInterval: CFTimeInterval = 0.8
    private static let defaultHeavyInterval: CFTimeInterval = 1.0
    
    @inline(__always)
    private static func canTrigger(lastAt: inout CFTimeInterval, minInterval: CFTimeInterval) -> Bool {
        let now = CACurrentMediaTime()
        if now - lastAt < minInterval { return false }
        lastAt = now
        return true
    }
    
    // MARK: - Haptic Feedback Methods
    
    /// Provides light impact feedback (throttled)
    static func light(minInterval: CFTimeInterval = defaultLightInterval) {
        guard canTrigger(lastAt: &lastLightAt, minInterval: minInterval) else { return }
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }
    
    /// Provides medium impact feedback (throttled)
    static func medium(minInterval: CFTimeInterval = defaultMediumInterval) {
        guard canTrigger(lastAt: &lastMediumAt, minInterval: minInterval) else { return }
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
    }
    
    /// Provides heavy impact feedback (throttled)
    static func heavy(minInterval: CFTimeInterval = defaultHeavyInterval) {
        guard canTrigger(lastAt: &lastHeavyAt, minInterval: minInterval) else { return }
        let generator = UIImpactFeedbackGenerator(style: .heavy)
        generator.impactOccurred()
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