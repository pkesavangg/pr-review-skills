# SDK Docs & Confluence Sync — code and docs ship together

The Sage SDK treats `docs/` as a **maintained, canonical source of truth**, and one of those docs — the BLE protocol spec — is a **mirror of Confluence page `1489993739`**. `CLAUDE.md` is explicit: the spec is "the byte-for-byte spec the ESP32 firmware ships against," `docs/PUBLIC-API.md` is "the canonical, side-by-side reference … part of the v1.0.0 contract," and `docs/CAPABILITY-CONTRACTS.md` is "the public contract for the consuming app team." When a PR changes code these docs describe but leaves the docs untouched, the docs silently rot and the next engineer (or the firmware team, or the consuming-app team) trusts a lie. This rule enforces the SDK's development contract: **a change to documented behavior updates the matching doc in the same PR, and — for the protocol spec — is mirrored to Confluence.**

This file supplies the SDK's source→doc map directly (the SDK repo declares no machine-readable map, so the orchestrator's generic docs-freshness check can't fire here — this rule replaces it for SDK repos). Severity uses the orchestrator's taxonomy; this file prescribes its own severities — do not re-classify. **If the SDK repo later adds a `docs/confluence.md` or a "Keeping docs current" section to its `CLAUDE.md`, prefer that map over the one below.**

---

## Source → doc → Confluence map

| Changed code area | Maintained doc(s) to update in the same PR | Confluence mirror |
|---|---|---|
| Public API — `External/` / `external/` (handler, delegates, public enums, public value types) | `docs/PUBLIC-API.md` **+** `docs/api-snapshots/vX.Y.Z.txt` **+** `CHANGELOG.md` | mirror if a public-API page is declared (no fixed page id known) |
| Capabilities — `Capabilities/` / `capabilities/` (protocol added/removed/changed) | `docs/CAPABILITY-CONTRACTS.md` **+** `CHANGELOG.md` | mirror if a capability page is declared (no fixed page id known) |
| Wire protocol — characteristic/service UUIDs, opcodes, byte layouts (`Enums/*Command*`, `Utils/GGUUIDManager*`, `Devices/` decode/encode) | `docs/Sage_Kettle_BLE_protocol_spec_v1.md` **+** `docs/protocol-fixtures.json` **+** `CHANGELOG.md` | **Confluence `1489993739`** (protocol spec — known page) |
| Core connection behavior (reconnect, service invalidation, disconnect, availability) | `docs/ENGINEERING_NOTES.md` and/or `docs/DISCONNECT_ON_SERVICE_INVALIDATION.md` | — |

**Never maps** (don't require a doc update): test files (`Tests/`, `src/test/`, `src/androidTest/`), generated code, demo-app changes (`SageBluetoothTestApp/`), and pure `*.md`/`docs/` edits themselves.

---

## P2 — Documented code changed but the mapped doc wasn't updated

The core rule. When a PR changes a source area in the map above but the mapped doc isn't in the same PR's changed-file list, the doc is now stale. This is checkable entirely from the PR (no external state), so it's a finding.

```
# Example: a PR edits External/GGIBluetoothHandler.swift (adds a public method)
# but docs/PUBLIC-API.md and docs/api-snapshots/ are not in the changed files → flag.
```

**Sniff.** Map each changed source file through the table above. For each mapped doc that is **not** also in the PR's changed-file list (from Step 1), record a gap. Group all gaps for one PR into a **single** comment.

**Fix.** Post one comment (top-level, no single line): `P2 — Docs not updated for a documented change · This PR changes <area(s)>, documented in <doc(s)>, but <doc(s)> aren't updated in the diff. Update them in this PR (regenerate the api-snapshot / protocol-fixtures where applicable), or note in the PR why the docs are unaffected.` If the SDK repo's `CLAUDE.md` makes docs part of Definition-of-Done, raise to **P1**.

**Do NOT flag** when the only changed files are tests/generated/demo/`*.md` (they never map), or when the mapped doc *is* in the change set. A doc updated "close enough" counts — this rule checks that the doc was touched, not that the wording is perfect.

---

## P2 — `CHANGELOG.md` not updated for a public / protocol change

The SDK keeps a Keep-a-Changelog `CHANGELOG.md`, and its entries cite the Confluence page + spec section for protocol changes (e.g. "Confluence 1489993739 v22, spec §7.6/§7.7"). A public-API or wire-protocol change with no changelog entry leaves consumers and the release process blind to what shipped.

**Sniff.** A change under `External/` (public symbol added/removed/changed) or to wire-protocol constants/layouts on `+` lines, while `CHANGELOG.md` is absent from the PR's changed-file list.

**Fix.** Add an entry under `## [Unreleased]` describing the change; for a protocol change, cite the spec section and the Confluence version as the existing entries do. Fold into the same comment as the docs-not-updated finding above — one comment per PR, not one per doc.

---

## Confluence verification (the protocol spec — page `1489993739`)

Confluence state isn't visible from a GitHub diff, so the depth of this check depends on whether the **Atlassian MCP** is available this session (look for a Confluence read tool: `getConfluencePage` — `mcp__claude_ai_Atlassian__getConfluencePage` interactively, `mcp__Atlassian__getConfluencePage` in the cloud routine).

**When the PR changes wire-protocol code or `docs/Sage_Kettle_BLE_protocol_spec_v1.md`, AND the MCP is available:**

1. Fetch the mirror page: `getConfluencePage` for id `1489993739`.
2. Compare the page against the PR's spec change — check the **version marker** (the spec doc's change-log rows cite "Mirrors Confluence 1489993739 v<N>"; the CHANGELOG cites the same) and the specific layout/section the PR touched (e.g. a characteristic length, an opcode, an error mapping).
3. If the Confluence page is **behind** the PR's spec/code (page version older than what the spec change references, or the changed layout isn't reflected), add to the summary: `Confluence 1489993739 appears behind this change (page @ v<N>, spec now references v<M>). Mirror the protocol update to Confluence — run /update-confluence.` This is surfaced as a **summary note**, not an inline `P`-level finding — the reviewer can read the page but can't prove the author *intended* to skip it.
4. If the page already reflects the change, note `Confluence 1489993739 is in sync` in the summary and move on.

**When the MCP is not available (or the change doesn't touch the protocol):** do **not** fetch anything. If the docs-not-updated rule above fired for a Confluence-mirrored doc, add a single **reminder** line to the Step 5 summary — `Reminder: mirror this protocol change to Confluence 1489993739 (couldn't verify wiki state) — run /update-confluence.` Never a finding, never re-review-tracked. This honors the orchestrator's "never flag what you couldn't check" guardrail.

Pre-commit (`/review`) never has PR context and treats Confluence as reminder-only regardless — the local-doc P2 still applies, the Confluence line is a reminder in the report.
