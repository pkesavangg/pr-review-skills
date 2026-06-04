//
//  ToastModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import SwiftUI

struct ToastModel: Equatable {
    var title: String?
    var message: String
    /// Optional view rendered at the very top of the card, before the title.
    /// Used for the multiple-readings counter row (e.g. "3 more readings received  VIEW").
    var headerView: AnyView?
    var btnTextView: AnyView?
    var onClick: () -> Void = {}
    var duration: Double = 3
    var onDismiss: (() -> Void)?
    var onActiveCountChanged: ((Int) -> Void)?
    
    static func == (lhs: ToastModel, rhs: ToastModel) -> Bool {
        lhs.title == rhs.title &&
        lhs.message == rhs.message &&
        lhs.duration == rhs.duration
        // Note: We exclude buttonView and onClick from equality check
        // as AnyView isn't Equatable and functions can't be compared
    }
}
