import Foundation

/// Validation errors of a form.
public struct ValidationErrors<Value> {
    private var errors: [ValidatorType: (hasError: Bool, value: Any?)] = [:]
    
    var hasError: Bool {
        errors.contains { $0.value.hasError }
    }
    
    mutating func update(
        for validator: Validator<Value>,
        value: Bool
    ) {
        errors.updateValue((hasError: !value, value: validator.value), forKey: validator.type)
    }

    /// Clears any recorded error for the given validator type.
    /// Used when a validator is removed so its stale error no longer keeps the control invalid.
    mutating func remove(for type: ValidatorType) {
        errors.removeValue(forKey: type)
    }

    /// Gets whether there is an error with the provided validator type.
    public subscript(_ type: ValidatorType) -> Bool {
        errors[type]?.hasError ?? false
    }
    
    /// Gets the value associated with a validator type
    public func value(for type: ValidatorType) -> Any? {
        errors[type]?.value
    }
}

public enum ValidatorType: Hashable {
    case required
    case email
    case min
    case max
    case minLength
    case maxLength
    case requiredTrue
    case noWhiteSpace
    case futureDate
    case matches
    case phoneNumber
    case passwordMatch
    case weightEqual
    case passwordDifferent
    case url
    case minValue
    case maxValue
    case skuMatch
    case duplicate
    case namePattern
    case alphanumeric
    case userNameUnavailable
    case reversedValues
    case maxLimit
    case numericOnly
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(String(describing: self))
    }
}
