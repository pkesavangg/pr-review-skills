# SwiftUI Implementation (Swift Charts)

A complete, self-contained growth chart for iOS 16+ / macOS 13+ using Apple's **Swift Charts** framework. Four pieces: data models, the LMS math helper, a JSON loader, and the chart view. Keep the math (`LMS`) separate from the view so it can be unit-tested against the worked example in `lms-and-percentile-math.md`.

`import Charts` requires iOS 16 / macOS 13 or later. Set that as the deployment target.

## 1. Data models

```swift
import Foundation

struct GrowthReference: Codable {
    let measure: String       // e.g. "weight-for-age"
    let sex: String           // "male" | "female"
    let unit: String          // "kg" | "cm"
    let ageUnit: String       // "months" | "years"
    let rows: [LMSRow]
}

struct LMSRow: Codable {
    let age: Double
    let L: Double
    let M: Double
    let S: Double
}

/// One point on a drawn percentile curve.
struct CurvePoint: Identifiable {
    let id = UUID()
    let age: Double
    let value: Double
    let percentile: Int       // 3, 15, 50, 85, 97, ...
}

/// A measurement the app is plotting for a specific child.
struct ChildMeasurement: Identifiable {
    let id = UUID()
    let age: Double
    let value: Double
}
```

## 2. The LMS math helper

This mirrors the formulas in `lms-and-percentile-math.md` exactly. `erf` comes from the C math library (available through `Foundation` on Apple platforms).

```swift
import Foundation

enum LMS {
    /// z-scores for the standard percentiles growth charts draw.
    static let standardPercentiles: [(percentile: Int, z: Double)] = [
        (3,  -1.88079), (5,  -1.64485), (10, -1.28155), (15, -1.03643),
        (25, -0.67449), (50,  0.0),     (75,  0.67449), (85,  1.03643),
        (90,  1.28155), (95,  1.64485), (97,  1.88079)
    ]

    /// Formula 1: measurement value at a given z-score. Handles the L == 0 branch.
    static func value(z: Double, l: Double, m: Double, s: Double) -> Double {
        if abs(l) < 1e-7 {
            return m * exp(s * z)
        }
        return m * pow(1 + l * s * z, 1 / l)
    }

    /// Formula 2: z-score for a measurement. Handles the L == 0 branch.
    static func zScore(value x: Double, l: Double, m: Double, s: Double) -> Double {
        if abs(l) < 1e-7 {
            return log(x / m) / s
        }
        return (pow(x / m, l) - 1) / (l * s)
    }

    /// Formula 3: standard normal CDF -> percentile (0...100).
    static func percentile(fromZ z: Double) -> Double {
        0.5 * (1 + erf(z / 2.0.squareRoot())) * 100
    }

    /// Convenience: percentile for a raw measurement given an LMS row.
    static func percentile(value x: Double, row: LMSRow) -> Double {
        percentile(fromZ: zScore(value: x, l: row.L, m: row.M, s: row.S))
    }

    /// Linearly interpolate L, M, S for an age between two rows.
    static func interpolatedRow(forAge age: Double, in rows: [LMSRow]) -> LMSRow? {
        guard let first = rows.first, let last = rows.last else { return nil }
        if age <= first.age { return first }
        if age >= last.age { return last }
        for i in 0..<(rows.count - 1) {
            let a = rows[i], b = rows[i + 1]
            if age >= a.age && age <= b.age {
                let t = (age - a.age) / (b.age - a.age)
                return LMSRow(age: age,
                              L: a.L + t * (b.L - a.L),
                              M: a.M + t * (b.M - a.M),
                              S: a.S + t * (b.S - a.S))
            }
        }
        return last
    }
}
```

## 3. Loader + curve generation

```swift
import Foundation

enum GrowthData {
    /// Load a bundled reference JSON (e.g. "sample_wtageinf_boys").
    static func load(_ name: String) -> GrowthReference? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(GrowthReference.self, from: data)
    }

    /// Build every percentile curve from a reference: one CurvePoint per age per percentile.
    static func curves(from reference: GrowthReference,
                       percentiles: [Int]? = nil) -> [CurvePoint] {
        let wanted = percentiles.map { set in
            LMS.standardPercentiles.filter { set.contains($0.percentile) }
        } ?? LMS.standardPercentiles

        return reference.rows.flatMap { row in
            wanted.map { p in
                CurvePoint(age: row.age,
                           value: LMS.value(z: p.z, l: row.L, m: row.M, s: row.S),
                           percentile: p.percentile)
            }
        }
    }
}
```

## 4. The chart view

`LineMark` for each percentile curve (grouped into series by percentile), and the child's measurements overlaid as `PointMark` + a connecting `LineMark`. The 50th curve is emphasized; the outer curves are muted; the child is a contrasting color.

```swift
import SwiftUI
import Charts

struct GrowthChartView: View {
    let reference: GrowthReference
    let childMeasurements: [ChildMeasurement]

    private var curves: [CurvePoint] { GrowthData.curves(from: reference) }

    var body: some View {
        Chart {
            // Percentile reference curves
            ForEach(curves) { point in
                LineMark(
                    x: .value("Age", point.age),
                    y: .value(reference.measure, point.value),
                    series: .value("Percentile", point.percentile)
                )
                .interpolationMethod(.catmullRom)
                .lineStyle(StrokeStyle(lineWidth: point.percentile == 50 ? 2.5 : 1))
                .foregroundStyle(color(for: point.percentile))
            }

            // The child's own measurements
            ForEach(childMeasurements) { m in
                LineMark(
                    x: .value("Age", m.age),
                    y: .value(reference.measure, m.value),
                    series: .value("Percentile", -1)   // own series, not a percentile
                )
                .foregroundStyle(.orange)
                .lineStyle(StrokeStyle(lineWidth: 2))

                PointMark(
                    x: .value("Age", m.age),
                    y: .value(reference.measure, m.value)
                )
                .foregroundStyle(.orange)
                .symbolSize(80)
            }
        }
        .chartXAxisLabel("Age (\(reference.ageUnit))")
        .chartYAxisLabel(reference.unit)
        .chartYScale(domain: .automatic(includesZero: false))
        .frame(height: 360)
        .padding()
    }

    /// 50th emphasized, inner curves mid-tone, outer curves faint.
    private func color(for percentile: Int) -> Color {
        switch percentile {
        case 50: return .blue
        case 15, 85: return .blue.opacity(0.6)
        default: return .blue.opacity(0.35)
        }
    }
}
```

## 5. Putting it together + reading a percentile

```swift
struct ContentView: View {
    var body: some View {
        if let reference = GrowthData.load("sample_wtageinf_boys") {
            let child = [
                ChildMeasurement(age: 0,  value: 3.4),
                ChildMeasurement(age: 3,  value: 6.6),
                ChildMeasurement(age: 6,  value: 8.3),
                ChildMeasurement(age: 9,  value: 9.7),
                ChildMeasurement(age: 12, value: 10.1)
            ]
            VStack(alignment: .leading, spacing: 8) {
                GrowthChartView(reference: reference, childMeasurements: child)
                // Live percentile readout for the latest visit:
                if let latest = child.last,
                   let row = LMS.interpolatedRow(forAge: latest.age, in: reference.rows) {
                    let p = LMS.percentile(value: latest.value, row: row)
                    Text("At \(Int(latest.age)) months, \(latest.value, specifier: "%.1f") kg "
                         + "≈ \(Int(p.rounded()))th percentile")
                        .font(.subheadline)
                        .padding(.horizontal)
                }
            }
        } else {
            Text("Could not load reference data.")
        }
    }
}
```

## Notes

- **Add the JSON to the app target.** Drag `sample_wtageinf_boys.json` (or your real converted file) into Xcode and confirm it's in *Target Membership → Copy Bundle Resources*, or `Bundle.main.url` returns `nil`.
- **Swap in real data** by replacing the JSON with a full conversion of the WHO/CDC file for the sex and chart type you need (see `data-sources.md`). The code doesn't change.
- **Unit-test the math**, not the view: assert `LMS.zScore(value: 9.7, l: -0.1600954, m: 9.476500305, s: 0.11218624)` ≈ `0.207` and `LMS.percentile(fromZ:)` of that ≈ `58`. That single test catches almost every wiring mistake.
- **Highlight bands** (shading between the 3rd and 97th) can be added with `AreaMark` if you want the classic filled look; keep the child's points on top.
- **Sex/chart-type switching**: load a different JSON per sex and measure; never transform one dataset into another.
