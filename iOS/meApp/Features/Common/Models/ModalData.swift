//
//  ModalData.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import SwiftUI

struct ModalData: Identifiable, Equatable {
    let id = UUID()
    var presentedView: AnyView = AnyView(EmptyView())
    var backdropDismiss: Bool = true
    var onDismiss: (() -> Void)? = nil
    
    static func == (lhs: ModalData, rhs: ModalData) -> Bool {
        lhs.id == rhs.id
    }
    
    // Since we can't compare closures, we need to ignore onDismiss in Equatable
    static func != (lhs: ModalData, rhs: ModalData) -> Bool {
        lhs.id != rhs.id
    }
}
