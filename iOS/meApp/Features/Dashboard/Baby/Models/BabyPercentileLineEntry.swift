//
//  BabyPercentileLineEntry.swift
//  meApp
//
//  One row from the WHO percentile-lines JSON (decigram values keyed by day-of-life).
//

import Foundation

struct BabyPercentileLineEntry: Codable {
    let day: Int
    let fifth: Int
    let tenth: Int
    let twentyFifth: Int
    let fiftieth: Int
    let seventyFifth: Int
    let ninetieth: Int
    let ninetyFifth: Int

    func value(for line: BabyPercentileLine) -> Int {
        switch line {
        case .fifth: return fifth
        case .tenth: return tenth
        case .twentyFifth: return twentyFifth
        case .fiftieth: return fiftieth
        case .seventyFifth: return seventyFifth
        case .ninetieth: return ninetieth
        case .ninetyFifth: return ninetyFifth
        }
    }
}
