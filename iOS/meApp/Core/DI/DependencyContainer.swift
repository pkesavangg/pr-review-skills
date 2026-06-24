//
//  DependencyContainer.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import SwiftUI

/// A singleton container used for managing and resolving dependencies across the app.
/// It stores dependencies by type and allows retrieving them via type-based lookup.
class DependencyContainer {
    static let shared = DependencyContainer()

    var dependencies: [String: Any] = [:]

    func register<T>(_ dependency: T) {
        let key = String(describing: T.self)
        dependencies[key] = dependency

        // Also store a reflected key to reduce type-name mismatch issues
        // between protocol existentials and concrete registrations.
        let reflectedKey = String(reflecting: T.self)
        dependencies[reflectedKey] = dependency
    }

    func resolve<T>(_ type: T.Type) -> T? {
        let key = String(describing: T.self)
        if let value = dependencies[key] as? T {
            return value
        }

        let reflectedKey = String(reflecting: T.self)
        if let value = dependencies[reflectedKey] as? T {
            return value
        }

        // Swift may represent existential types with an "any " prefix.
        let anyKey = "any \(key)"
        if let value = dependencies[anyKey] as? T {
            return value
        }

        // Fallback: return the first registered dependency that conforms to T.
        return dependencies.values.first { $0 is T } as? T
    }
}

/// A property wrapper that injects a dependency from the `DependencyContainer`.
/// The dependency is resolved lazily on first access and then cached locally.
@propertyWrapper
struct Injector<Value> {
    private var value: Value?

    init() {}

    var wrappedValue: Value {
        mutating get {
            if value == nil {
                value = DependencyContainer.shared.resolve(Value.self)
            }
            guard let resolvedValue = value else {
                let keys = DependencyContainer.shared.dependencies.keys.sorted().joined(separator: ", ")
                fatalError("Dependency \(Value.self) is not registered in DependencyContainer. Registered keys: [\(keys)]")
            }
            return resolvedValue
        }
        set {
            value = newValue
        }
    }

    var projectedValue: Injector<Value> {
        get {
            return self
        }
        set {
            self = newValue
        }
    }
}
