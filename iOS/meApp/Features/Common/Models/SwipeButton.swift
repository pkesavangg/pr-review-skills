//
//  SwipeButton.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//


import SwiftUI

struct SwipeButton: Identifiable {
    let id = UUID()
    let tint: Color //background color of the button
    let action: () -> Void //action to be performed when the button is tapped
    let label: () -> AnyView //label to be displayed on the button
}