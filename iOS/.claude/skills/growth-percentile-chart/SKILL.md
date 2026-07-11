---
name: growth-percentile-chart
description: >-
  Build pediatric growth percentile charts (weight-for-age, height/length-for-age,
  head-circumference-for-age, BMI-for-age, weight-for-length) and compute a child's
  percentile or z-score from official WHO/CDC reference data using the LMS method.
  Use this skill whenever the user wants to draw, render, or explain a baby/child
  growth chart, plot percentile curves, place a child's measurements on such a chart,
  convert a measurement into a percentile or z-score (or the reverse), or wire growth
  data into an app — especially a SwiftUI app using Swift Charts. Trigger it even when
  the user only says "growth chart", "percentile graph", "centile curve", "WHO chart",
  "CDC chart", "plot my baby's weight", or asks how percentile lines are calculated,
  where the standard data comes from, or how to structure the data for a chart. Prefer
  this skill over ad-hoc plotting because getting the LMS math, the sex/age split, and
  the data provenance right is what makes the chart clinically correct.
---

# Growth Percentile Chart

This skill produces clinically correct pediatric growth percentile charts and the math behind them. A percentile chart answers two questions: *where does this child sit compared to a reference population of the same age and sex,* and *are they tracking along the same curve over time.* Both depend on official reference data plus one specific statistical method — the **LMS method**. Get those right and everything else (rendering, colors, tooltips) is presentation.

## What you are building

A growth chart is a set of smooth **percentile curves** (typically the 3rd, 5th, 10th, 25th, 50th, 75th, 90th, 95th, 97th) drawn over an age axis, with the child's own measurements plotted as points on top. The curves are *not* fitted to the child — they come from a reference population. The child's dots are laid over that fixed backdrop.

There are five standard chart types, each measuring one thing against age (or against length):

- weight-for-age
- length/height-for-age (length lying down under 2 years, standing height at/after 2 years)
- head-circumference-for-age
- BMI-for-age
- weight-for-length/height

Each type has **separate reference data for boys and girls**. Never mix sexes or chart types.

## The workflow

Follow these steps in order. Read the referenced files when you reach the step that needs them.

### 1. Pin down the inputs

Confirm four things before touching data or code:

1. **Chart type** — weight-for-age, height-for-age, head-circumference, BMI, or weight-for-length.
2. **Sex** — boys and girls use different reference tables.
3. **Age range / standard** — this decides *which* reference dataset. See step 2.
4. **What to render** — just the percentile curves? The child's points too? A computed percentile/z-score readout?

If the user hasn't said, ask — but you can proceed with sensible defaults (weight-for-age, the age range they mentioned, WHO under 2 / CDC 2–20) and state the assumption.

### 2. Choose and obtain the reference data

The percentile curves are generated from **LMS parameters** (three numbers per age per sex) published by WHO and CDC. Which dataset to use is driven by age. The widely followed convention (CDC's own recommendation) is:

- **Birth to 24 months → WHO Child Growth Standards** (the CDC re-hosts these as ready-to-use LMS files).
- **2 to 20 years → CDC growth charts.**

`references/data-sources.md` has the exact download locations, file layout (the `WTAGEINF`-style tables), the birth-to-36-months vs birth-to-24-months options, column meanings, and the important age-indexing quirk (age is stored at the half-month point). Read it when you need to fetch or format the data.

The maximum age depends on the dataset: WHO Child Growth Standards run birth to **5 years (60 months)**; the WHO Growth Reference and CDC charts continue to **19–20 years**. Infant charts specifically usually cap at **24 or 36 months**.

`assets/sample_wtageinf_boys.json` is a small, real slice of WHO/CDC boys weight-for-age LMS data (birth to 24 months) in the exact JSON shape the code expects, so the app can run immediately and the real file can be dropped in later.

### 3. Compute the curves and the child's percentile

This is the heart of the skill and the part that must be exact. All of it lives in `references/lms-and-percentile-math.md`: the LMS formulas (both the `L ≠ 0` and `L = 0` cases), the z-score↔percentile conversions, the table of z-values for the standard percentiles, a worked numeric example you can use as a unit test, and the two error traps (forgetting the `L = 0` branch, and using the standing-height table for a lying-down measurement). Read that file before writing any calculation code — do not reconstruct the formulas from memory, small sign errors produce curves that look plausible but are wrong.

The short version, so the shape is clear:

- **Value at a percentile** (used to draw each curve). Convert the percentile to its z-score `Z`, then
  `X = M · (1 + L·S·Z)^(1/L)` when `L ≠ 0`, or `X = M · exp(S·Z)` when `L = 0`.
  Do this for every age row to get one curve; repeat per percentile.
- **Percentile from a measurement** (used for the child). Given measurement `X`,
  `Z = ((X/M)^L − 1) / (L·S)` when `L ≠ 0`, or `Z = ln(X/M) / S` when `L = 0`, then percentile `= Φ(Z)·100`.

`M`, `L`, `S` come from the reference row matching the child's sex and age.

### 4. Render it

For a SwiftUI app, read `references/swiftui-implementation.md`. It contains a complete, self-contained implementation: the `Codable` data models, an `LMS` math helper, a JSON loader, and a `GrowthChartView` built on the **Swift Charts** framework (`LineMark` for the percentile curves, `PointMark`/`LineMark` for the child's plotted measurements), plus styling notes (emphasize the 50th, gray the outer curves, contrasting color for the child). It targets iOS 16+ / macOS 13+.

For any other target (web, Python, etc.) the same math applies — generate `(age, value)` series per percentile and hand them to whatever plotting library is in play. Keep the computation separate from the rendering so the reference data and the LMS helper can be reused and unit-tested.

## Correctness checklist

Before calling a chart done, verify:

- Boys' data on a boys' chart, girls' on a girls'; the two are visibly different.
- The `L = 0` branch is handled (rare in these tables but present — skipping it silently corrupts affected ages).
- The child's dots are plotted raw against the reference curves — never re-fit the curves to the child.
- The worked example in `references/lms-and-percentile-math.md` reproduces the documented percentile (a fast, decisive unit test).
- Curves are labeled and the units/axis (kg vs cm, months vs years) match the chart type.
- The chart is framed as a *comparison to a reference population*, not a score — a steadily tracking low percentile is normal; a sudden crossing of curves is the real signal.

## Files in this skill

- `SKILL.md` — this workflow.
- `references/lms-and-percentile-math.md` — the LMS method, all formulas, z↔percentile conversions, worked example, pitfalls. Read before writing calculation code.
- `references/data-sources.md` — where the WHO/CDC data lives, file formats, age ranges, indexing quirks. Read before fetching or formatting data.
- `references/swiftui-implementation.md` — full SwiftUI + Swift Charts implementation. Read when building the iOS/macOS app.
- `assets/sample_wtageinf_boys.json` — real sample LMS data (boys, weight-for-age, 0–24 mo) in the expected JSON shape.
