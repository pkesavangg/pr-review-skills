//
//  AuthRoute.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import SwiftUI

// This file defines the authentication routes for the app.
enum AuthRoute: Routable {
    case login
    case signup
    
    var body: some View {
        switch self {
        case .login:
            EmptyView() // TODO: Replace with login view
        case .signup:
            SignupScreen() 
        }
    }
}
