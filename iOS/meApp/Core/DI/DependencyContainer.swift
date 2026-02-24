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
    }

    func resolve<T>(_ type: T.Type) -> T? {
        let key = String(describing: T.self)
        return dependencies[key] as? T
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
            return value!
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
