//
//  TextInputConfig.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

struct TextInputConfig {
    var label: String
    var placeholder: String?
    var inputType: TextFieldType = .text
    var submitLabel: SubmitLabel = .next
    var errorMessage: String? = nil
    var isDisabled: Bool = false
    var focusField: FocusField = .none
    
    // Custom icon support
    var customIcon: String? = nil
    var onCustomIconTap: (() -> Void)? = nil
    
    // Bank input specific properties
    var maxLength: Int = 3
    var maxValue: Double? = nil
    var allowWholeNumbers: Bool = false
    /// Determines whether leading zeros should be preserved when `allowWholeNumbers` is true.
    /// - Note: Defaults to `false` to keep the previous trimming behaviour. Set to `true` when
    ///         you need to display values like "0385" without removing the leading zero.
    var showPrefixZero: Bool = false
}
