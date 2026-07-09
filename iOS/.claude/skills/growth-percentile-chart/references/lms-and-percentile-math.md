# The LMS Method and Percentile Math

Everything on a growth chart is generated from three numbers per age, per sex: **L**, **M**, **S**. This file gives the exact formulas, the z-score↔percentile conversions, a worked example to use as a unit test, and the traps to avoid. Do not paraphrase the formulas from memory — copy them.

## What L, M, S mean

The LMS method (Cole & Green) describes the distribution of a measurement at each age with three parameters:

- **M** — the **median** (the 50th-percentile value at that age).
- **S** — the **generalized coefficient of variation** (how spread out the distribution is).
- **L** — the **power of the Box-Cox transformation** (how skewed it is). `L` is often negative for weight and can be near or exactly `0`.

Because the distribution is skewed, you cannot just do `median ± k·SD`. The Box-Cox power `L` corrects for the skew, which is the entire reason the LMS method exists.

## Formula 1 — value at a given percentile (used to DRAW the curves)

To find the measurement value `X` that corresponds to a z-score `Z` (and a percentile maps to a fixed `Z` — see the table below):

```
if L != 0:   X = M * (1 + L * S * Z) ** (1 / L)
if L == 0:   X = M * exp(S * Z)
```

`**` means exponentiation (raise to the power). `exp` is the natural exponential.

To draw the 50th-percentile curve: compute `X` at `Z = 0` for every age row, then connect the points. For the 3rd percentile use `Z = -1.8808`, for the 97th use `Z = +1.8808`, and so on. One percentile = one pass over all age rows = one curve.

## Formula 2 — percentile from a measurement (used to PLACE a child)

Given a child's measurement `X` and the L, M, S for their sex and age:

```
if L != 0:   Z = ((X / M) ** L - 1) / (L * S)
if L == 0:   Z = ln(X / M) / S
```

Then convert `Z` to a percentile with the standard normal CDF (Formula 3). This is the inverse of Formula 1.

## Formula 3 — z-score ⇄ percentile

A z-score is a position on the standard normal distribution; the percentile is the area to its left, times 100.

```
percentile = Phi(Z) * 100          # Phi = standard normal cumulative distribution function
Z          = probit(p / 100)       # probit = inverse of Phi
```

`Phi` can be computed from the error function, which is available in C/Swift/most languages as `erf`:

```
Phi(z) = 0.5 * (1 + erf(z / sqrt(2)))
```

For the reverse (percentile → z) you have two options:

- **Drawing standard curves:** just use the fixed z-values in the table below. No inverse function needed.
- **Arbitrary percentiles:** use a rational approximation of the inverse normal (e.g. Acklam's algorithm) or your platform's statistics library.

### Standard percentile → z-score table

These are the z-values for the percentiles growth charts conventionally draw. Hard-code these to render the standard curves.

| Percentile | z-score  |
|-----------:|:---------|
| 3rd        | -1.88079 |
| 5th        | -1.64485 |
| 10th       | -1.28155 |
| 15th       | -1.03643 |
| 25th       | -0.67449 |
| 50th       |  0.00000 |
| 75th       |  0.67449 |
| 85th       |  1.03643 |
| 90th       |  1.28155 |
| 95th       |  1.64485 |
| 97th       |  1.88079 |

(Symmetry: the z for the nth percentile is the negative of the z for the (100−n)th.)

## Worked example — use this as a unit test

Published CDC/WHO example. A **9-month-old boy weighing 9.7 kg**, weight-for-age. From the reference table:

```
L = -0.1600954
M =  9.476500305
S =  0.11218624
```

Apply Formula 2 (`L != 0`):

```
Z = ((9.7 / 9.476500305) ** -0.1600954 - 1) / (-0.1600954 * 0.11218624)
Z ≈ 0.207
```

Then Formula 3: `Phi(0.207) * 100 ≈ 58`. So this child is at roughly the **58th percentile**. If your code reproduces `Z ≈ 0.21` and `~58th`, the math is wired correctly. If it doesn't, stop and fix it before rendering anything.

## Pitfalls

- **Forgetting the `L == 0` branch.** It is uncommon in these tables but it does occur. If you only implement the `L != 0` formula, affected ages divide by zero or produce garbage that still *plots as a smooth-ish line*, so it hides. Always branch on `L == 0` (in floating point, test `abs(L) < 1e-7`).
- **Wrong table for the child's age.** Under 2 years the length reference assumes the child was measured **lying down**; at/after 2 years the height reference assumes **standing**. Standing is ~0.7 cm shorter than lying for the same child, so using the wrong table shifts every percentile. Match the table to how the child was actually measured.
- **Mixing sexes.** Boys and girls have different L, M, S. A boy on the girls' table lands at the wrong percentile silently.
- **Interpolation.** Reference tables give L, M, S at discrete ages (often each half-month or month). For an in-between age, linearly interpolate L, M, and S across the two nearest rows, then apply the formulas — don't interpolate the final percentile values.
- **Reading percentile as a grade.** Report it as a comparison ("bigger than about 58 of 100 boys this age"), and treat *change across curves over time* as the meaningful clinical signal, not a single number.
