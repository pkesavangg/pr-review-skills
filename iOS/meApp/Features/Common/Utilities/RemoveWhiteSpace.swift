//
//  RemoveWhiteSpace.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/06/25.
//

// MARK: - RemoveWhiteSpace
/// A utility function to remove excessive whitespace from a string.
func removeWhiteSpace(_ value: String?) -> String {
    guard let value = value, !value.isEmpty else { return "" }
    let collapsed = value.replacingOccurrences(of: " {2,}", with: " ", options: .regularExpression)
    return collapsed.trimmingCharacters(in: .whitespacesAndNewlines)
}
