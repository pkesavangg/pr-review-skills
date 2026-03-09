import Combine
//
//  ManualEntryScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//
import SwiftUI

/// Observes keyboard appearance and publishes its height so views can adjust their layout accordingly.
/// Usage:
/// ```swift
/// @StateObject private var keyboard = KeyboardResponder()
/// ...
/// .padding(.bottom, keyboard.currentHeight)
/// ```
final class KeyboardResponder: ObservableObject {
    /// Current height of the keyboard. `0` when the keyboard is hidden.
    @Published var currentHeight: CGFloat = 0

    private var cancellables = Set<AnyCancellable>()

    init() {
        let willShow = NotificationCenter.default
            .publisher(for: UIResponder.keyboardWillShowNotification)
            .compactMap { $0.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect }
            .map { $0.height }

        let willHide = NotificationCenter.default
            .publisher(for: UIResponder.keyboardWillHideNotification)
            .map { _ in CGFloat(0) }

        Publishers.Merge(willShow, willHide)
            .receive(on: RunLoop.main)
            .sink { [weak self] height in
                self?.currentHeight = height
            }
            .store(in: &cancellables)
    }
}
