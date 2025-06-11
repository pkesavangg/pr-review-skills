//
//  TextFieldType.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


enum TextFieldType {
    case text
    case email
    case password
    case number
    case metric
}

enum FocusField: Hashable {
    case firstName
    case lastName
    case email
    case password
    case confirmPassword
    case zipCode
    case weight
    case bodyFat
    case none
}
