//
//  KeyboardObserverModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/07/25.
//
import SwiftUI

// MARK: - Keyboard Observer Modifier
struct KeyboardObserverModifier: ViewModifier {
    @StateObject private var keyboardObserver = KeyboardObserver()
    @Binding var keyboardHeight: CGFloat
    
    init(keyboardHeight: Binding<CGFloat>) {
        self._keyboardHeight = keyboardHeight
    }
    
    func body(content: Content) -> some View {
        content
            .onReceive(keyboardObserver.$keyboardHeight) { height in
                keyboardHeight = height
            }
    }
}
