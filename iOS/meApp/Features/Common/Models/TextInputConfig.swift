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
    var errorMessage: String?
    var isDisabled: Bool = false
    var focusField: FocusField = .none
    
    // Custom icon support
    var customIcon: String?
    var onCustomIconTap: (() -> Void)?
    
    // Bank input specific properties
    var maxLength: Int = 3
    var maxValue: Double?
    var allowWholeNumbers: Bool = false
    /// Determines whether leading zeros should be preserved when `allowWholeNumbers` is true.
    /// - Note: Defaults to `false` to keep the previous trimming behaviour. Set to `true` when
    ///         you need to display values like "0385" without removing the leading zero.
    var showPrefixZero: Bool = false
    /// When `true` in decimal mode, typing only zeros clears the field instead of showing "0.0".
    /// Defaults to `false` to preserve standard decimal formatting behaviour.
    var clearZeroValue: Bool = false
    /// Number of digits to place after the decimal point in decimal mode.
    /// Defaults to `1`. Set to `3` for baby kg/lb fields to match Baby App formatting.
    var decimalPlaces: Int = 1
}
