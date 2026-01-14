//
//  String+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//

import Foundation

extension String {
    func slice(from start: Int, to end: Int) -> String {
        guard start >= 0, end <= self.count, start < end else { return "" }
        let startIdx = self.index(self.startIndex, offsetBy: start)
        let endIdx = self.index(self.startIndex, offsetBy: end)
        return String(self[startIdx..<endIdx])
    }
    
    /// Returns the first alphabetic character in the string.
    /// Falls back to the first character if no letter is found.
    func firstAlphabeticCharacter() -> String {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        guard let firstChar = trimmed.first else { return "" }

        if let letter = trimmed.first(where: { $0.isLetter }) {
            return String(letter)
        }

        return String(firstChar)
    }
}
