import Foundation
import UIKit


/// A validator that performs synchronous validation.
///
/// ## Adding Custom Validators
/// To add a new custom validator:
/// 1. Add a new case to `ValidatorType` enum if needed
/// 2. Create an extension for the appropriate Value type
/// 3. Add your validation logic
///
/// Example:
/// ```swift
/// // 1. Add to ValidatorType (in ValidationErrors.swift)
/// case phoneNumber
///
/// // 2. Add extension for String validators
/// extension Validator where Value == String {
///     /// Custom phone number validator
///     public static let phoneNumber = Validator(type: .phoneNumber) { value in
///         // 3. Add validation logic
///         let pattern = "^\\d{10}$"
///         return NSPredicate(format: "SELF MATCHES %@", pattern)
///             .evaluate(with: value)
///     }
///
///     /// Custom validator with parameters
///     public static func customRange(_ min: Int, _ max: Int) -> Validator {
///         Validator(type: .custom, value: [min, max]) { value in
///             guard let length = Int(value) else { return false }
///             return length >= min && length <= max
///         }
///     }
/// }
/// ```
///
/// Usage:
/// ```swift
/// var control = FormControl("", validators: [.required, .phoneNumber])
/// var rangeControl = FormControl("", validators: [.customRange(5, 10)])
/// ``

/// A validator that performs synchronous validation.
public struct Validator<Value>: Identifiable {
    public typealias ValidatorFn = (Value) -> Bool
    
    /// Type of the validator
    public let type: ValidatorType
    /// Value used in validation (e.g. minimum length, maximum value)
    public let value: Any?
    /// Validator function.
    public private(set) var fn: ValidatorFn
    /// Identifier of a validator of the same type.
    public var id: String { String(describing: type) }
    
    /// Creates a validator with the provided type and validation value.
    public init(type: ValidatorType, value: Any? = nil, fn: @escaping ValidatorFn) {
        self.type = type
        self.value = value
        self.fn = fn
    }
}

// MARK: - String Validators
extension Validator where Value == String {
    /// Validator that requires the control have a non-empty value.
    public static let required = Validator(type: .required) { !$0.isEmpty }
    
    /// Validator that requires the control's value pass an email validation test.
    public static let email = Validator(type: .email) { string in
        Rule.email(string)
    }
    
    /// Validator that requires the control's value to pass a URL validation test.
    public static let url = Validator(type: .url) { string in
        Rule.url(string)
    }
    
    /// Validator that prevents whitespace-only values
    public static let noWhiteSpace = Validator(type: .noWhiteSpace) { value in
        !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    /// Validator that requires the length of the control's value to be greater than
    /// or equal to the provided minimum length.
    public static func minLength(_ minimum: Int) -> Validator {
        Validator(type: .minLength, value: minimum) { value in
            Rule.minLength(value, minimumLength: minimum)
        }
    }
    
    /// Validator that requires the length of the control's value to be less than
    /// or equal to the provided maximum length.
    public static func maxLength(_ maximum: Int) -> Validator {
        Validator(type: .maxLength, value: maximum) { value in
            Rule.maxLength(value, maximumLength: maximum)
        }
    }
    
    /// Validator that checks if the value matches another string value
    static func matches(_ otherValue: @autoclosure @escaping () -> String) -> Validator {
        Validator(type: .matches) { value in
            value == otherValue()
        }
    }
}

// MARK: - Integer Validators
extension Validator where Value == Int {
    /// Validator that requires the control's value to be greater than
    /// or equal to the provided number.
    public static func min(_ minimum: Int) -> Validator {
        Validator(type: .min, value: minimum) { value in
            Rule.min(value, minimumValue: minimum)
        }
    }
    
    /// Validator that requires the control's value to be less than
    /// or equal to the provided number.
    public static func max(_ maximum: Int) -> Validator {
        Validator(type: .max, value: maximum) { value in
            Rule.max(value, maximumValue: maximum)
        }
    }
}

// MARK: - Boolean Validators
extension Validator where Value == Bool {
    /// Validator that requires the boolean value to be true
    public static let requiredTrue = Validator(type: .requiredTrue) { $0 == true }
}

// MARK: - Date Validators
extension Validator where Value == Date {
    /// Validator that ensures the date is not in the future
    public static let futureDate = Validator(type: .futureDate) { value in
        value <= Date()
    }
}

// MARK: - Validation Rules
private struct Rule {
    /// A regular expression that matches valid e-mail addresses.
    static let emailPattern = ##"^(?=.{1,254}$)(?=.{1,64}@)[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"##
    
    /// A regular expression that matches valid URLs.
    static let urlPattern = ##"^(https?://)?(www\\.)?[a-zA-Z0-9@:%._\\+~#?&//=]{2,256}\\.[a-z]{2,6}\\b(?:[-a-zA-Z0-9@:%._\\+~#?&//=]*)$"##
    
    static func min(_ value: Int, minimumValue: Int) -> Bool {
        value >= minimumValue
    }
    
    static func max(_ value: Int, maximumValue: Int) -> Bool {
        value <= maximumValue
    }
    
    static func required(_ string: String) -> Bool {
        !string.isEmpty
    }
    
    static func email(_ string: String) -> Bool {
        NSPredicate(format:"SELF MATCHES %@", emailPattern)
            .evaluate(with: string)
    }
    
    static func minLength(_ string: String, minimumLength: Int) -> Bool {
        string.count >= minimumLength
    }
    
    static func maxLength(_ string: String, maximumLength: Int) -> Bool {
        string.count <= maximumLength
    }
    
    static func url(_ string: String) -> Bool {
        guard let url = URL(string: string) else { return false }
        return UIApplication.shared.canOpenURL(url)
    }
} 
