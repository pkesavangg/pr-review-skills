# Reference Data Sources

The percentile curves are only as correct as the reference data behind them. This file says where the official L, M, S data lives, how the files are laid out, and the quirks that bite. Verify the current URLs when you fetch — public-health sites reorganize, but the file formats below are stable.

## Which dataset for which age

The convention CDC itself recommends:

- **Birth to 24 months → WHO Child Growth Standards.** These describe how healthy, well-nourished (largely breastfed) children *should* grow, from a multi-country study. Most of the world, including India, uses WHO for young children.
- **2 to 20 years → CDC growth charts** (US reference). Other countries may use their own national references or the WHO Growth Reference (5–19 years).

Age ranges by dataset:

- WHO Child Growth Standards: birth to **5 years (60 months)**.
- WHO Growth Reference: **5 to 19 years**.
- CDC charts: **2 to 20 years** (with infant tables also provided birth–24 or birth–36 months).

So "the oldest a baby chart goes" is typically **24 or 36 months**; the full child/adolescent range goes to **19–20 years**.

## Where to download

**CDC-hosted WHO LMS files (birth–24 months)** — the easiest source, already in LMS form as XLS and CSV, split by sex:
`https://www.cdc.gov/growthcharts/who-data-files.htm`
Provides: weight-for-age, length-for-age, weight-for-length, and head-circumference-for-age, Boys and Girls separately.

**CDC's own LMS files (2–20 years, plus birth–36 months infant tables):**
`https://www.cdc.gov/growthcharts/cdc-data-files.htm`
Provides: weight-for-age, stature/length-for-age, weight-for-stature, BMI-for-age, head-circumference-for-age.

**WHO source (birth–5 years and 5–19 years), the original tables:**
`https://www.who.int/tools/child-growth-standards`
WHO distributes these as per-indicator text/zip files, again split by sex.

Each measurement type × sex is its own file. Download exactly the ones your app needs and no more.

## File layout

A CDC/WHO LMS table (e.g. the infant weight-for-age table, historically named `WTAGEINF`) has one row per age and these columns:

| Column        | Meaning                                                        |
|---------------|----------------------------------------------------------------|
| `Sex`         | 1 = male, 2 = female (CDC convention)                          |
| `Agemos`      | Age in months (see the half-month quirk below)                 |
| `L`           | Box-Cox power                                                  |
| `M`           | Median                                                         |
| `S`           | Coefficient of variation                                       |
| `P3` … `P97`  | Pre-computed percentile values (3rd, 5th, 10th, …, 95th, 97th) |

You only strictly need `L`, `M`, `S` (plus `Sex` and `Agemos`) — from those you can compute every percentile and any z-score with the formulas in `lms-and-percentile-math.md`. The `P3`…`P97` columns are convenient for sanity-checking your own computation: compute the value at that percentile's z and confirm it matches the published column.

## Quirks that bite

- **Age is stored at the half-month point.** A row labeled `1.5` months represents the whole span `1.0 ≤ age < 2.0` months. The one exception is birth, stored as a single point. Account for this when you match a child's exact age to a row, or interpolate.
- **Length vs height at 24 months.** The under-2 tables use *recumbent length* (lying down); the 2+ tables use *standing height*. There is a real ~0.7 cm discontinuity. Keep them as separate charts; don't splice them into one continuous curve without a note.
- **Sex coding.** CDC files use `1`/`2` for male/female. Map that to your own model explicitly so a stray integer never silently selects the wrong table.
- **Units.** Weight in kilograms, lengths/heights and head circumference in centimeters, BMI in kg/m². Label axes accordingly; don't hand a chart kg values under a "cm" axis.

## Minimal JSON shape for an app

Convert the CSV you download into a compact JSON keyed by what your app looks up. The bundled `assets/sample_wtageinf_boys.json` follows this shape (boys, weight-for-age, birth–24 months):

```json
{
  "measure": "weight-for-age",
  "sex": "male",
  "unit": "kg",
  "ageUnit": "months",
  "rows": [
    { "age": 0,  "L": 3.176131, "M": 3.346417, "S": 0.146674 },
    { "age": 1,  "L": 2.322805, "M": 4.470962, "S": 0.135140 }
  ]
}
```

Keep the LMS values at full published precision (they are given to many decimal places for a reason — truncating them visibly bends the outer curves).
