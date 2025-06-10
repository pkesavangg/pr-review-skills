//
//  TextInputConfig.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

struct TextInputConfig {
    var label: String
    var placeholder: String
    var inputType: TextFieldType = .text
    var submitLabel: SubmitLabel = .next
    var errorMessage: String? = nil
    var isDisabled: Bool = false
    
    // Custom icon support
    var customIcon: String? = nil
    var onCustomIconTap: (() -> Void)? = nil
    
    // Bank input specific properties
    var maxLength: Int = 3
    var maxValue: Double? = nil
    var allowWholeNumbers: Bool = false
}
