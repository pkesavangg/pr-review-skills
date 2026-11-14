# iOS unit tests — layout, shape and copy assertions

Project-tuned rules for **where a test lives and how it is shaped**. `test-hygiene.md` stays the home for flake sources (sleeps, live singletons, disk-backed stores); this file owns organisation.

## Read the repo's testing guide first

These rules fire only when the repo documents them. Look for `iOS/meAppTests/docs/UNIT_TESTING.md` (meApp), or any `docs/UNIT_TESTING.md` / `TESTING.md` beside the test target, plus `docs/common/TESTING_STANDARD.md`. Read it before reviewing — it changes while the test tree migrates, and **where it disagrees with a rule here, the guide wins**. No guide → skip this file entirely.

Fires on files under the unit-test target (`*Tests/**`) and on `project.pbxproj` / `*.xctestplan` changes.

---

## P1 — Test file listed in the target's `membershipExceptions`

A synchronised test folder compiles every file in it. A file added to `membershipExceptions` never compiles and never runs, and nothing reports it: the suite looks green while the test is gone.

**Sniff.** `+` lines in `project.pbxproj` inside a `membershipExceptions` block naming a `*Tests.swift` file.

**Fix.** Remove the entry. Adding, moving or deleting a test file needs no `project.pbxproj` edit.

---

## P1 — Test parallelisation re-enabled

Every suite calls `TestDependencyContainer.reset()` on one global container. Run suites concurrently and one suite wipes another's registrations mid-test — failures that appear on developer devices and not on CI.

**Sniff.** `parallelizable` flipped to `true` in a `*.xctestplan`, or the scheme switched off the test plan.

**Fix.** Keep `parallelizable: false`. A suite that only passes in parallel has a hidden order dependency; fix the suite.

---

## P1 — Test touches the app's real store (`PersistenceController.shared`)

`PersistenceController.shared.context` is the on-disk store. A test that writes through it reads rows left by the app and earlier tests, so the result depends on the device.

```swift
let service = BabyService()                                   // defaults to the shared on-disk context
PersistenceController.shared.context.insert(profile)
```

**Sniff.** `PersistenceController.shared` in any `+` line under `*Tests/`; a service built with its default context in a test that seeds or asserts persisted rows.

**Fix.** Inject a `ModelContext` over a per-test in-memory `ModelContainer` (`BabyService(context: makeInMemoryContext())`), and hold the container for the test's lifetime.

---

## P2 — New test file not at the mirrored path, or a second suite for one subject

Tests mirror the source tree: `meApp/Data/Services/DeviceService.swift` → `meAppTests/Data/Services/DeviceServiceTests.swift`. One subject, one suite.

**Sniff.** An added `*Tests.swift` whose path, minus `Tests`, doesn't match the subject's source path; or `rg -l "struct <Subject>Tests|class <Subject>Tests"` already finds a suite elsewhere.

**Fix.** Add the cases to the existing suite, or move the new file to the mirrored path. Feature-local mocks go in `Features/<F>/Mocks`, reusable ones in `Support/Mocks`; nothing at the target root.

---

## P2 — `Extra` / `Coverage` / `More` / `Additional` in a test file or type name

A coverage gap is closed by adding cases to the existing suite. `LoginStoreExtraTests`, `DeviceServiceCoverageTests` and `EdgeCoverage` split one subject across files nobody finds.

**Sniff.** Added test file or type names matching `(Extra\d*|Coverage|EdgeCoverage|More|Additional)`.

**Fix.** Fold the cases into `<Subject>Tests.swift`, or into a behaviour split (next rule).

---

## P2 — Suite grown past 600 lines or 50 tests without a behaviour split

**Sniff.** A changed `*Tests.swift` that is over 600 lines or 50 `@Test`s after this PR, with new tests added to the base file.

**Fix.** Split by behaviour, not by date: `Suites/<Subject>Tests+<Behaviour>.swift` holding `extension <Subject>Tests { @Suite("<Behaviour>") struct <Behaviour> { … } }`. Shared helpers stay in the base file.

---

## P2 — Unnecessary test (adds coverage, protects nothing)

A test earns its place when it would fail on a real bug. Tests that only pin what the compiler, the language or a constant already guarantees cost run time and maintenance, and break on harmless changes.

```swift
@Test func weightUnit_rawValue() { #expect(WeightUnit.kg.rawValue == "kg") }        // restates the enum
@Test func init_setsName() { #expect(Profile(name: "A").name == "A") }               // memberwise init
@Test func title() { #expect(GoalStrings.title == "Goal") }                          // restates Strings
```

**Sniff.** An added test whose only assertion: compares an enum `rawValue`/`case` to its literal; reads back a property just passed to an initialiser; compares a `Strings`/token constant to a literal; checks a synthesised `Codable`/`Equatable`/`Hashable` conformance with no custom logic; or repeats an existing case in the same suite with the same inputs. Also any test added in a PR that only changes copy, colours or spacing.

**Fix.** Delete it. If the type has real logic (custom decoding, a computed property, a mapping table), test that logic instead. Coverage on such code comes from the tests of the code that uses it.

---

## P2 — Three or more look-alike tests that should be one `@Test(arguments:)`

```swift
@Test func rounds_up() { #expect(14.95.roundedToDisplayedWeight == 15.0) }
@Test func rounds_down() { #expect(14.94.roundedToDisplayedWeight == 14.9) }
@Test func rounds_small() { #expect(0.05.roundedToDisplayedWeight == 0.1) }
```

**Sniff.** 3+ added tests in one suite whose bodies differ only in literal inputs and expected values.

**Fix.** One parameterised test; every case stays as a row, so nothing is lost:

```swift
@Test("roundedToDisplayedWeight rounds to one decimal", arguments: [(14.95, 15.0), (14.94, 14.9), (0.05, 0.1)])
func roundedToDisplayedWeight(input: Double, expected: Double) {
    #expect(input.roundedToDisplayedWeight == expected)
}
```

---

## P2 — UI copy asserted with a literal, or a test that only restates a `Strings` constant

Tests check **which** message appears, not **what it says**. The wording lives in the feature `Strings` file and Figma; a literal in a test breaks on every copy change and catches nothing.

```swift
#expect(toast.message == "This email is already in use.")    // copied text
#expect(SignupStrings.emailInUse == "This email is already in use.")   // restates Strings
```

**Sniff.** `#expect(... == "<sentence>")` on a `message`/`title`/`text`/`label` property; a test whose only assertion compares a `*Strings.<name>` to a literal; a test-local mirror type named `*TestText`. In a PR that only changes copy, any **added** test.

**Fix.** `#expect(toast.message == SignupStrings.emailInUse)`. Test copy only when a function builds it (interpolation, plurals, a choice by product) — as `arguments:` rows of that function's test. When copy changes, edit the `Strings` constant and nothing else; a failing test was pinning copy — point it at the constant or delete it. Log-message assertions may use literals.

---

## P2 — Test with no assertion

**Sniff.** An added `@Test` with no `#expect` / `#require`, or one whose only check is "does not throw" while its name promises a state.

**Fix.** Assert the primary result and at least one side effect.

---

## Nit — `// Arrange` / `// Act` / `// Assert` labels, or a ticket id in a test name

Keep the three parts apart with a blank line; labels add nothing. Ticket ids rot; git blame keeps the trail.

**Sniff.** `+` lines `//\s*(Arrange|Act|Assert|Given|When|Then)\b` in test files; `[A-Z]{2,6}-\d+` inside `@Test("…")` or a test function name.

**Fix.** Delete the labels. Name tests by behaviour: `method success: expected behavior`, `method validation failure: …`, `method API failure: …`. This shape supersedes `test-hygiene.md`'s naming Nit when the repo guide defines it.
