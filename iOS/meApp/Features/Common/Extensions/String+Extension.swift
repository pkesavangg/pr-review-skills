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
}
