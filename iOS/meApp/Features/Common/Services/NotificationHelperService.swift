//
//  NotificationHelperService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import Foundation

@MainActor
class NotificationHelperService: ObservableObject {
    static let shared = NotificationHelperService()
    
    @Published var isOverlayActive: Bool = false
}
