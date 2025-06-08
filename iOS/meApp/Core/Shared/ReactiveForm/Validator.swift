import Foundation

/// A validator that performs synchronous validation.
public struct Validator<Value>: Identifiable {
    public typealias ValidatorFn = (Value) -> Bool
    
    /// Identifier of a validator of the same type.
    public private(set) var id: UUID
    /// Validator function.
    public private(set) var fn: ValidatorFn
    
    /// Creates a validator with the provided identifier.
    public init(id: UUID, fn: @escaping ValidatorFn) {
        self.id = id
        self.fn = fn
    }
    
    /// Creates a validator with a unique identifier.
    public init(_ fn: @escaping ValidatorFn) {
        self.id = UUID()
        self.fn = fn
    }
}

private enum Identifier {
    static let min = UUID()
    static let max = UUID()
    static let minLength = UUID()
    static let maxLength = UUID()
    static let futureDate = UUID()
    static let matches = UUID()
}

extension Validator where Value == String {
    /// Validator that requires the control have a non-empty value.
    public static let required = Validator(Rule.required)
    
    /// Validator that requires the control's value pass an email validation test.
    public static let email = Validator(Rule.email)
    
    /// Validator that requires the length of the control's value to be greater than
    /// or equal to the provided minimum length.
    public static func minLength(_ minimum: Int) -> Validator {
        Validator(id: Identifier.minLength) { value in
            Rule.minLength(value, minimumLength: minimum)
        }
    }
    
    /// Validator that requires the length of the control's value to be less than
    /// or equal to the provided maximum length.
    public static func maxLength(_ maximum: Int) -> Validator {
        Validator(id: Identifier.maxLength) { value in
            Rule.maxLength(value, maximumLength: maximum)
        }
    }
}

extension Validator where Value == Int {
    /// Validator that requires the control's value to be greater than
    /// or equal to the provided number.
    public static func min(_ minimum: Int) -> Validator {
        Validator(id: Identifier.min) { value in
            Rule.min(value, minimumValue: minimum)
        }
    }
    
    /// Validator that requires the control's value to be less than
    /// or equal to the provided number.
    public static func max(_ maximum: Int) -> Validator {
        Validator(id: Identifier.max) { value in
            Rule.max(value, maximumValue: maximum)
        }
    }
}

extension Validator where Value == String {
    static func matches(_ otherValue: @autoclosure @escaping () -> String) -> Validator {
        Validator(id: Identifier.matches) { value in
            value == otherValue()
        }
    }
}



extension Validator where Value == Bool {
    public static let requiredTrue = Validator { $0 == true }
}

extension Validator where Value == String {
    public static let noWhiteSpace = Validator { value in
        // Must not be all whitespace
        !(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !value.isEmpty)
    }
}

extension Validator where Value == Date {
    public static let futureDate = Validator(id: Identifier.futureDate) { value in
        value <= Date()
    }
}

private struct Rule {
    /// A regular expression that matches valid e-mail addresses.
    static let emailPattern = ##"^(?=.{1,254}$)(?=.{1,64}@)[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"##
    
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
    
    static func requiredTrue(_ value: Bool?) -> Bool {
        value ?? false
    }
} 
