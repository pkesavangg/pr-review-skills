//
//  AuthRoute.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import SwiftUI

// This file defines the authentication routes for the app.
enum AuthRoute: Routable {
    case login(String?)
    case signup
    
    var body: some View {
        switch self {
        case .login(let email):
            LoginScreen(prefilledEmail: email)
        case .signup:
            SignupScreen() 
        }
    }
}
