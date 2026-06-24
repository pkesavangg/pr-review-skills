//
//  KeyboardObserver.swift
//  meApp
//
//  Created by AI Assistant
//

import Combine
import SwiftUI

/// A utility class to observe keyboard show/hide events and manage keyboard height state.
/// This class provides a reusable way to track keyboard visibility across multiple views.
@MainActor
class KeyboardObserver: ObservableObject {
    /// The current height of the keyboard
    @Published var keyboardHeight: CGFloat = 0
    
    /// Whether the keyboard is currently visible
    @Published var isKeyboardVisible: Bool = false
    
    private var keyboardWillShowObserver: NSObjectProtocol?
    private var keyboardWillHideObserver: NSObjectProtocol?
    
    /// Initializes the keyboard observer and sets up notification listeners
    init() {
        setupObservers()
    }
    
    /// Sets up the keyboard notification observers.
    private func setupObservers() {
        keyboardWillShowObserver = NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillShowNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            // This closure is nonisolated; hop to the main actor.
            guard let strongSelf = self else { return }
            Task { @MainActor in
                strongSelf.keyboardWillShow(notification)
            }
        }

        keyboardWillHideObserver = NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillHideNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            // This closure is nonisolated; hop to the main actor.
            guard let strongSelf = self else { return }
            Task { @MainActor in
                strongSelf.keyboardWillHide()
            }
        }
    }
    
    /// Removes the keyboard notification observers
    private func removeObservers() {
        if let observer = keyboardWillShowObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        if let observer = keyboardWillHideObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }
    
    /// Handles keyboard will show notification
    /// - Parameter notification: The keyboard notification containing frame information
    private func keyboardWillShow(_ notification: Notification) {
        guard let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }
        
        keyboardHeight = keyboardFrame.height
        isKeyboardVisible = true
    }
    
    /// Handles keyboard will hide notification
    private func keyboardWillHide() {
        keyboardHeight = 0
        isKeyboardVisible = false
    }
}
