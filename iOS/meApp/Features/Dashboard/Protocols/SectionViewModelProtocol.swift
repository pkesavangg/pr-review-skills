//
//  SectionViewModelProtocol.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// Protocol defining common functionality for all section view models
@MainActor
protocol SectionViewModelProtocol: ObservableObject {
    // MARK: - Published Properties
    var selectedPoint: BathScaleWeightSummary? { get set }
    var selectedDate: Date? { get set }
    var showCrosshair: Bool { get set }
    var scrollPosition: Date { get set }
    var isScrolling: Bool { get set }
    /// The preferred, possibly snapped, date to propagate to the store on selection.
    /// Defaults to `selectedDate` but allows specialized view models to override behavior.
    var preferredSelectedDate: Date? { get }
    
    // MARK: - Chart Configuration
    var chartFrame: CGRect { get }
    var yAxisDomain: ClosedRange<Double> { get }
    var yAxisTicks: [Double] { get }
    
    // MARK: - Dependencies
    var dashboardStore: DashboardStore? { get }
    
    // MARK: - Period-specific properties
    var timePeriod: TimePeriod { get }
    var visibleDomainLength: TimeInterval { get }
    var maxGapForConnectedSegments: TimeInterval { get }
    var pointSize: CGFloat { get }
    var hasXAxis: Bool { get }
    var dateRange: ClosedRange<Date> { get }
    
    // MARK: - Common Computed Properties
    var chartOperations: [BathScaleWeightSummary] { get }
    var chartSeriesData: [GraphSeries] { get }
    /// Chart series points that fall within the currently visible X-domain
    var visibleChartSeriesData: [GraphSeries] { get }
    var goalWeight: Double { get }
    var displayWeight: Double? { get }
    var weightLabel: String { get }
    var xAxisValues: [Date] { get }
    var isAtLeftBoundary: Bool { get }
    
    // MARK: - Stroke & Point Sizing
    var lineWidth: CGFloat { get }
    var basePointDiameter: CGFloat { get }
    var selectedPointDiameter: CGFloat { get }
    var basePointArea: CGFloat { get }
    var selectedPointArea: CGFloat { get }
    func pointArea(isSelected: Bool) -> CGFloat
    func symbolArea(forDiameter diameter: CGFloat) -> CGFloat
    
    // MARK: - Initialization and Configuration
    func configure(with store: DashboardStore)
    
    // MARK: - Chart State Management
    func updateYAxisConfiguration()
    func updateChartFrame(_ frame: CGRect)
    
    // MARK: - Scroll Management
    func handleScrollPositionChange(_ newPosition: Date?)
    func handleScrollStart()
    func handleScrollEnd()
    
    // MARK: - Selection Management
    func handleChartSelection(at date: Date?)
    func clearSelection()
    
    // MARK: - Goal Chip Positioning
    func getGoalChipPosition() -> (yPosition: CGFloat, placement: GoalPlacement)
    func getGoalChipXOffset() -> CGFloat
    
    // MARK: - Chart Position Calculations
    func getChartPosition(for date: Date, value: Double) -> CGPoint?
    /// Returns the actual X value that should be plotted for a given data date.
    /// Default behavior is to use the same date; specific periods can override
    /// to shift points (e.g., center monthly averages between month ticks).
    func plotXDate(for original: Date) -> Date
    
    // MARK: - X-Axis Label Generation
    func formatXAxisLabel(for date: Date) -> String?
    
    // MARK: - Chart Content Helpers
    func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]]
    func shouldShowSolidLine(for date: Date) -> Bool
    func formatSelectedXAxisLabel() -> String?
    
    // MARK: - Data Management
    func refreshData()
    func handleSettingsChange()
    func updateScrollPosition(to position: Date)
    func initializeChart()
    func syncYAxisFromStore()
    
    // MARK: - Animation Control
    var shouldAnimateChartData: Bool { get }
}

