# Code comments — "no comment unless it is needed"

A **lens**, like the other `code-standards/` files: it runs alongside the iOS (`swift.md`) and Android (`kotlin.md`) pipelines and the SDK track, on **added or changed lines only**. It never flags a comment the PR did not touch — sweeping old comments is a tracked cleanup, not a review finding.

Appium/E2E code has its own comment rules in `appium/code-organization.md`; don't run this file on `*.ts`.

## Read the repo's comment policy first

Look for, in order: a "No comment unless it is needed" principle in the repo `CLAUDE.md`, a `.claude/skills/comment-style/SKILL.md`, a `Comments` section in `docs/common/CONTRIBUTING.md`. meApp has all three.

- **Policy found** → every rule below fires at the severity written. The posted comment must make sense on its own, without the policy text.
- **No policy** → only the ticket-id rule and the commented-out-code rule fire, both as **Nit**. Everything else is skipped: a repo that never adopted the rule shouldn't be reviewed against it.

**Never flag (organisation, not narration):** the Xcode file header, `// MARK: -` headers that group a type's real sections, `swiftlint:disable`/`detekt` suppressions, license headers, KDoc on a public Kotlin declaration that adds what the name can't, `///` on a public API whose purpose the name can't carry.

**One comment per file.** Collect every hit in a file into a single inline comment on the first line, listing the other line numbers. Ten comment findings on one file is noise.

---

## P2 — Ticket id, Figma node id, release version or `§` reference in a comment

They rot: the ticket closes, the node moves, the version ships. Git blame already carries the trail.

```swift
// MOB-3872: weightless entries must keep zero (see Figma 8-2145, since 5.2.0)
```

**Sniff.** On `+` comment lines (`//`, `///`, `/* */`, `/** */`, `#` in shell): `[A-Z]{2,6}-\d+`, `node-id=`, `\b\d+:\d{3,}\b` beside "Figma", `\b\d+\.\d+\.\d+\b`, `§\s*\d`.

**Fix.** Keep the reason, drop the reference: `// Weightless entries keep zero.` If the rule matters, it belongs in the feature doc's *Why it works this way* table.

---

## P2 — Commented-out code

Dead code in a comment is never run, never compiled, and never deleted. Version control keeps the old version.

```kotlin
// val legacy = repository.fetchLegacy(id)
// if (legacy != null) return legacy
```

**Sniff.** `+` comment lines whose body parses as code: ends in `)`, `{`, `}`, `;`, or contains ` = `, `return `, `let `, `val `, `func `, `fun `.

**Fix.** Delete it.

---

## P2 — Comment longer than two lines

The policy is one line, two at most. Longer reasoning goes in the feature doc, the PR description or the commit message.

```swift
/// This method checks if the user has reached the maximum number of accounts.
/// If they have, it shows an alert. Otherwise it navigates to the login screen.
/// Before the multi-account change this always went to login, which caused a crash
/// when the sixth account was added.
```

**Sniff.** Three or more consecutive added comment lines (`//` or `///`, or a `/* */` / `/** */` block spanning 3+ lines) on new or changed code.

**Fix.** Keep the one WHY that removing would invite a bug, as one line; move the rest to the feature doc. If no line is a WHY, delete the block.

---

## P2 — `- Parameter` / `- Returns` / `- Throws`, or KDoc `@param` / `@return` that restate the signature

```swift
/// Fetches an account by its ID.
/// - Parameter id: The account ID.
/// - Returns: The account.
func fetchAccount(byId id: UUID) async throws -> Account
```

**Sniff.** `+` lines matching `///\s*- (Parameters?|Returns|Throws)`, or `@param` / `@return` whose text repeats the parameter or type name.

**Fix.** Delete the doc comment. Test: if it still reads correctly with the method name covered, it adds nothing.

---

## Nit — What-comment, section label, or trailing comment that restates the line

```swift
// Check if max accounts reached
if accounts.count >= maxAccounts { … }

// MARK: - Body
var body: some View { … }

throw error // No offline fallback
```

**Sniff.** A `+` comment whose words mostly repeat identifiers on the next line; a `// MARK:` directly above a single declaration; a trailing `// …` after a statement that names what the statement does.

**Fix.** Delete. A comment stays only when it explains **why**, removing it would invite a bug (ordering, threading, an SDK quirk, a deliberate omission), and the doc can't say it next to the line.

---

## P2 — Behaviour rule deleted from a comment but not added to the doc

When a PR trims a comment that carried a real rule ("`nil` here is transient, not a logout"), the rule must land in the feature doc in the same PR, or it is lost.

**Sniff.** A `-` comment line that states a behaviour or constraint, with no matching change under `iOS/docs/features/`, `iOS/docs/system/` or `docs/common/` in the diff.

**Fix.** Add the rule to the feature doc's *Behaviour* or *Why it works this way* section in this PR.

---

Test files: `// Arrange` / `// Act` / `// Assert` labels and ticket ids in test names are owned by `ios/test-organization.md` and `compose/test-organization.md` — don't raise them here too.
