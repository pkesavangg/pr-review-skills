//
//  GraphViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

class GraphViewModel: ObservableObject {
    // Published state for selection and annotation
    @Published var selectedEntry: BathScaleOperationDTO? = nil
    @Published var annotationHeight: CGFloat = 0
    @Published var selectedPointY: CGFloat = 0
    @Published var chartHeight: CGFloat = 0

    let yAxisTicks: [Double] = stride(from: 175, through: 190, by: 5).map { $0 }

    func xAxisDomain(for operations: [BathScaleOperationDTO]) -> ClosedRange<Date> {
        let dates = operations.compactMap { $0.date }
        guard let min = dates.min(), let max = dates.max() else {
            let now = Date()
            return now...now
        }
        return min == max ? (min.addingTimeInterval(-1800))...(max.addingTimeInterval(1800)) : min...max
    }
    
    /// Alignment for rule mark annotation label
    func ruleMarkAlignment(for selected: BathScaleOperationDTO, in operations: [BathScaleOperationDTO]) -> Alignment {
        guard let idx = operations.firstIndex(where: { $0.id == selected.id }) else { return .center }
        switch idx {
        case 0: return .leading
        case operations.count - 1: return .topTrailing
        default: return .center
        }
    }
    
    /// Get selected entry and its Y coordinate at a given drag location
    func getSelectedEntry(at location: CGPoint, proxy: ChartProxy, operations: [BathScaleOperationDTO]) -> (entry: BathScaleOperationDTO, pointY: CGFloat)? {
        guard let date: Date = proxy.value(atX: location.x) else { return nil }
        guard let nearest = operations
            .compactMap({ op -> (BathScaleOperationDTO, Date)? in
                guard let d = op.date else { return nil }
                return (op, d)
            })
                .min(by: { abs($0.1.timeIntervalSince(date)) < abs($1.1.timeIntervalSince(date)) })?.0 else {
            return nil
        }
        if let weight = nearest.weight, let y = proxy.position(forY: weight) {
            return (entry: nearest, pointY: y)
        }
        return nil
    }
    
    /// Drag gesture for use in chartOverlay
    func dragGesture(
        proxy: ChartProxy,
        operations: [BathScaleOperationDTO],
        selectedWeight: Binding<Double?>
    ) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                self.updateSelectedEntry(
                    at: value.location,
                    using: proxy,
                    operations: operations,
                    selectedWeight: selectedWeight
                )
            }
            .onEnded { value in
                self.updateSelectedEntry(
                    at: value.location,
                    using: proxy,
                    operations: operations,
                    selectedWeight: selectedWeight
                )
            }
    }
    
    /// Update the selection and pointY for the annotation
    private func updateSelectedEntry(
        at location: CGPoint,
        using proxy: ChartProxy,
        operations: [BathScaleOperationDTO],
        selectedWeight: Binding<Double?>
    ) {
        guard let result = getSelectedEntry(at: location, proxy: proxy, operations: operations) else {
            selectedWeight.wrappedValue = nil
            selectedEntry = nil
            return
        }
        selectedEntry = result.entry
        selectedPointY = result.pointY
        selectedWeight.wrappedValue = result.entry.weight
    }
    
    func annotationBubbleOffset(
        pointRadius: CGFloat = .radius2XL,
        extraOffset: CGFloat = 5,
        yCorrection: CGFloat = 180) -> CGFloat {
        let bubbleHeight = annotationHeight
        return selectedPointY - (chartHeight / 2) + pointRadius + extraOffset - (bubbleHeight / 2) - yCorrection
    }
}
