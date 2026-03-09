//
//  IntegrationError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/06/25.
//

import Foundation

enum IntegrationError: LocalizedError {
    case userConflict
    
    var errorDescription: String? {
        switch self {
        case .userConflict:
            return "User conflict occurred during integration."
        }
    }
}
