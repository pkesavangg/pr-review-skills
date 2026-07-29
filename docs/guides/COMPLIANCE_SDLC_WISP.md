# Compliance — Secure SDLC & WISP

Standing compliance record for `gg-engineering/meApp` against the two Me.Health control programs:
the [Secure SDLC Program](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1476296742/Secure+SDLC+Program)
and the [Written Information Security Program (WISP)](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245708290/Written+Information+Security+Program+WISP).

**Last audited:** 2026-07-27 · **Branch:** `develop` · **Auditor:** Kesavan (Mobile Lead)

| Field | Value |
|-------|-------|
| Repository | `gg-engineering/meApp` |
| Default branch | `main` · active integration branch `develop` |
| Stack | iOS (Swift/SwiftUI) + Android (Kotlin/Compose) monorepo |
| CI provider | CircleCI (manual config, **not** Faber) |
| Data classification | **PHI** — weight, body composition, blood pressure, baby growth |
| Team appendix | [Appendix B: Mobile Engineering](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1247707162/Appendix+B+Mobile+Engineering) |

> This doc is **maintained**, not a snapshot. Re-run the checks in
> [§4 How to verify](#4-how-to-verify) and update the tables + the date above.

---

## 1. Score

Scored on the leadership 1–5 compliance scale. The two programs are scored **separately**.

| Program | Score | Rationale |
|---|:---:|---|
| **Secure SDLC** | **3 — Partially Compliant** | Core controls implemented and enforced in CI. Gaps are in *evidence*: Appendix B unfilled, no release sign-off records, branch-protection config unreadable, Android coverage floor below standard. |
| **WISP** | **3 — Partially Compliant** | Technical controls are real and enforced (secure credential storage, secrets scanning, HIPAA lint rules, ATS/network config). The written mobile PHI-handling / logging / retention control doc does not exist. |

**Scale:** 5 = fully compliant, evidenced, would pass an audit · 4 = minor gaps / doc cleanup ·
**3 = core controls in place, meaningful evidence or doc gaps** · 2 = ad hoc, would fail · 1 = unaddressed.

**The 3-vs-4 line:** *if you can produce the artifact on request it is a 4+; if not it is a 3 or below.*
Three SDLC artifacts and one WISP artifact cannot be produced today — see [§3 Pending register](#3-pending-register).

---

## 2. Audit results

### 2.1 Secure SDLC

| Category | Status | Details |
|----------|--------|---------|
| Required files | ✅ PASS | 6/6 required present (+ 4 bonus) |
| CI pipeline | ⚠️ PARTIAL | 6/7 — no SAST / SonarQube quality gate |
| Branch protection | ⚠️ PARTIAL | Both branches protected; config detail unreadable (no admin scope) |
| Code quality | ✅ PASS | SwiftLint + Detekt/ktlint + Lefthook pre-commit & commit-msg |
| Security scanning | ✅ PASS | Gitleaks (config + CI + hook) + Dependabot + OWASP |
| Test coverage | ⚠️ PARTIAL | iOS gated at 80%; **Android floor is 50%**, ratcheting toward 80% |
| Traceability | ✅ PASS | Jira ID enforced at commit; branch + CI filters on `MOB-*` |
| Release evidence | ❌ FAIL | Tags exist; no completed Release Sign-Off Checklist for any release |
| Team appendix | ❌ FAIL | Appendix B is 100% "Pending owner input" |

#### Required files — PASS (6/6)
| Check | Status | Evidence |
|---|---|---|
| `README.md` | ✅ | 108 lines, non-empty |
| `CODEOWNERS` | ✅ | `* @gg-engineering/me-health` — team ref, not individuals |
| `.gitignore` | ✅ | present |
| `LICENSE` | ✅ | Proprietary — DMD Brands, LLC |
| `.editorconfig` | ✅ | present (+ `Android/.editorconfig`) |
| CI config | ✅ | `.circleci/config.yml` |
| *Bonus* | ✅ | `SECURITY.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `.github/PULL_REQUEST_TEMPLATE.md` + issue templates |

#### CI pipeline — PARTIAL (6/7)
iOS + Android jobs are branch-filtered to `develop`, `main`, `MA-*`, `MOB-*`.

| Check | Status | Evidence |
|---|---|---|
| Build | ✅ | `ios-build` (xcodebuild), `android-build` (assembleDebug) |
| Test | ✅ | `ios-unit-tests` (iPhone 16 sim), `android-test`, `android-instrumented` |
| Coverage gate | ⚠️ | iOS `xccov` @ **80%** (UI layer excluded) · Android JaCoCo @ **50%** — see finding #1 |
| Static analysis | ✅ | `ios-swiftlint` (`--strict`) · `android-lint` + detekt |
| Secrets scanning | ✅ | `gitleaks` job, every branch, working tree |
| Dependency scanning | ✅ | `android-owasp-scan` (weekly cron, Mondays) + Dependabot |
| **SAST / SonarQube** | ❌ | No `sonar`/`semgrep` reference anywhere in CI — finding #5 |

**Skip-guard note (MOB-1562, fixed):** `skip_if_unchanged` diffs the whole branch against its
merge-base with `develop`, never `HEAD~1`, and never skips on `develop`/`main`. The earlier
false-green-on-merge-commit defect is closed.

#### Branch protection — PARTIAL
- `main` → `protected: true` ✅ · `develop` → `protected: true` ✅
- Behavioural evidence is strong: **last 15 merged PRs all show `APPROVED`**, no direct-to-branch merges.
- `GET /branches/{b}/protection` returns **HTTP 404** for both — the account holds `maintain`, not
  `admin`. Approval count, required status checks, and force-push/deletion restrictions are therefore
  **asserted but not evidenced** — finding #3.
- Repository ruleset **"Main Branch Protection"** (id `5676470`) has `enforcement: "disabled"`.
  Protection is coming from classic branch protection instead; the dormant ruleset should be
  enabled or deleted so it does not read as a lapsed control — finding #7.

#### Code quality — PASS
- iOS: `iOS/.swiftlint.yml` — includes custom **HIPAA rules** (`no_print_or_nslog`, no direct
  `UserDefaults`, no hardcoded credentials) plus snapshot-boundary and accessibility rules.
- Android: `Android/config/detekt/detekt.yml` + `detekt-baseline.xml`.
- Hooks via **Lefthook** (`.lefthook.yml`): pre-commit = detekt, swiftlint, gitleaks, testtags;
  commit-msg = Jira ticket enforcement (`[A-Z]+-[0-9]+`).
- ⚠️ Each hook degrades to a **warning** when its binary is absent (`command -v … || echo "Warning: …
  skipping"`). Local gates are advisory; CI is the enforcing layer — finding #14.
- `core.hooksPath` must be activated per machine via `lefthook install`.

#### Security scanning — PASS
- `.gitleaks.toml` (167 lines) — extends the default ruleset with custom AWS/healthcare patterns.
- `.github/dependabot.yml` — swift + gradle, weekly · `android-owasp-scan` · `SECURITY.md`
  (reporting channel + 48-hour acknowledgement).
- Last live scan (2026-07-22) clean; one false positive on a gitignored kapt build stub.

#### Test coverage — PARTIAL
| Platform | Enforced floor | Standard | Gap |
|---|---|---|---|
| iOS | **80%** on non-UI layers (`xccov`, UI excluded per CLAUDE.md) | 80% | none |
| Android | **50%** (`Android/app/build.gradle.kts:434`) | 80% | **30 points** |

Android's floor is a deliberate ratchet (MOB-1010) — actual is ~55%, raised step-by-step toward 80%
via MOB-963 / MOB-964 / MOB-967 / MOB-1101, never lowered. It is a documented, defensible exception,
but it is **not** 80% and must not be reported as such.

#### Release evidence — FAIL
| Artifact | State |
|---|---|
| Git tags matching published versions | ✅ `v5.0.0` … `v5.0.3` |
| Release notes | ⚠️ `CHANGELOG.md` stops at **5.0.2**; 5.0.3 shipped and tagged but is unlogged |
| Jira release / epic per release | ⚠️ not linked from the release record |
| Completed Release Sign-Off Checklist | ❌ none exist for any release |
| QA sign-off + device/OS matrix record | ❌ testing happens; the record is not retained |
| Rollback / phased-release plan | ❌ not documented |

[Appendix B](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1247707162/Appendix+B+Mobile+Engineering)
requires each production release to produce all of the above, and the
[Release Sign-Off Checklist](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1286307841/Release+Sign-Off+Checklist)
requires three named approvals — Product Owner, QA Lead, Tech Lead. The approvals genuinely happen;
they are not recorded.

---

### 2.2 WISP

The WISP has 15 domains plus two standalone policies. Most are org-level and owned by the Security
Officer, not the mobile team. Only the domains below are **app-scope** — the ones this repo can
provide evidence for. The rest are marked out of scope with their real owner.

| # | Domain | Scope | Status | Evidence / gap |
|---|---|---|---|---|
| 01 | [Security Governance](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245249628/01+-+Security+Governance) | Org | ➖ | Security Officer |
| 02 | [Risk Management](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244823593/02+-+Risk+Management) | Org | ➖ | Security Officer |
| 03 | [Access Control](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245904957/03+-+Access+Control) | **Shared** | ⚠️ | GitHub team `@gg-engineering/me-health` via CODEOWNERS ✅; **no periodic access-review record** |
| 04 | [Human Resources Security](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244233862/04+-+Human+Resources+Security) | Org | ➖ | HR / Security Officer |
| 05 | [Asset Management](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245249648/05+-+Asset+Management) | Org | ➖ | IT |
| 06 | [Physical Security](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244823613/06+-+Physical+Security) | Org | ➖ | Facilities |
| 07 | [Network Security](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244954661/07+-+Network+Security) | **Shared** | ⚠️ | Production is HTTPS under full ATS ✅; Android `network_security_config` ✅; **a cleartext-HTTP ATS exception for the dev IP ships in the production `Info.plist`** |
| 08 | [Operations Security](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244233881/08+-+Operations+Security) | **Shared** | ✅ | CI/CD controls, change management via PR + Jira |
| 09 | [Secure Development](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245708374/09+-+Secure+Development) | **App** | ✅ | SwiftLint HIPAA rules, detekt, gitleaks, PR review, PR template ("no secrets, tokens, or PHI committed") |
| 10 | [Cryptography](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245708393/10+-+Cryptography) | **App** | ⚠️ | Tokens in Keychain (iOS) / `SecureTokenStore` EncryptedSharedPreferences (Android) ✅; **PHI at rest in SwiftData/Room relies on OS full-disk encryption — no app-layer encryption and no written statement of that decision** |
| 11 | [Vendor Management](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245675548/11+-+Vendor+Management) | Org | ➖ | Security Officer |
| 12 | [Incident Management](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245675568/12+-+Incident+Management) | **Shared** | ⚠️ | `SECURITY.md` reporting channel + 48 h ack ✅; **no mobile incident / rollback runbook** |
| 13 | [Business Continuity](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245904976/13+-+Business+Continuity) | Org | ➖ | Security Officer |
| 14 | [Compliance & Audit](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245708412/14+-+Compliance+Audit) | **Shared** | ⚠️ | This document; Appendix B unfilled |
| 15 | [Privacy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244823769/15+-+Privacy) | **App** | ❌ | **No mobile PHI-handling control doc** — see finding #2 |

Two standalone policies sit alongside the numbered domains and are org-owned:
[Workstation and Endpoint Management Policy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1566048257/Workstation+and+Endpoint+Management+Policy)
and [Acceptable Use Policy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1565720581/Acceptable+Use+Policy).

**Secure storage — verified present**

| Platform | Credentials / tokens | PHI at rest |
|---|---|---|
| iOS | `Data/Services/KeychainService.swift`, `Core/Storage/KeychainAccess.swift` ✅ | SwiftData — OS Data Protection only |
| Android | `core/network/SecureTokenStore.kt` (EncryptedSharedPreferences) ✅ | Room — OS FDE only, no SQLCipher |

**Logging — the structural gap**

`LoggerService` (iOS) **persists log rows to disk** and can **upload them to the server** for support.
There is no redaction or allow-list layer between a call site and that pipeline.

Reviewed call sites log *identifiers only* — e.g. `EntryService.swift:294` logs
`entryId=…, accountId=…`, not measurement values. **No PHI values were found in current log
statements.** The finding is preventive, not an active leak: SwiftLint blocks `print()`/`NSLog()`,
but nothing inspects what is passed *into* `LoggerService`, so a future
`message: "Saved weight: \(entry.value)"` would reach disk and then the server unchallenged.

---

## 3. Pending register

Everything that must close to reach **4 — Substantially Compliant**. Owners are the accountable
party, not necessarily the doer.

| # | Sev | Program | Pending item | What "done" looks like | Owner | SLA |
|---|---|---|---|---|---|---|
| 1 | **High** | SDLC | Android coverage floor is 50%, standard is 80%. Prior audit reported it as 80% — corrected here | Either continue the ratchet with dates per step, or file a **documented coverage exception** approved by the Tech Lead. Do not report 80% until the gate says 80% | Kesavan | 30 d |
| 2 | **High** | WISP | **Mobile PHI-handling control doc** does not exist (domains 10 + 15) | Written doc covering: PHI at rest (SwiftData/Room + OS Data Protection), what may enter logs, what MetricKit/Crashlytics transmit, retention, and wipe-on-logout / account-delete behaviour | Kesavan + Security | 30 d |
| 3 | **High** | SDLC | Branch-protection config unreadable — behavioural evidence only | Admin exports `branches/{main,develop}/protection` JSON, or a Settings → Branches screenshot, attached to this doc | DevOps | 14 d |
| 4 | **High** | SDLC | **Appendix B: Mobile Engineering** is 100% "Pending owner input" — page status reads *"Action Required – Mobile Owner Validation Needed"* | Fill platform inventory, branch/release flow, build & signing, testing/device matrix, PHI handling, release evidence. Most of it is derivable from this repo | Kesavan | 30 d |
| 5 | Medium | SDLC | No SAST / SonarQube quality gate | Add a `sonar-scanner` (or Semgrep) job + `sonar.project_key`, **or** file a formal waiver arguing SwiftLint + detekt + OWASP + gitleaks are sufficient | Kesavan | 30 d |
| 6 | Medium | SDLC | No completed Release Sign-Off Checklist for any release | Complete one for **5.0.4** while it is in flight — PO + QA + Tech Lead named approvals, rollback plan, device matrix — and link it from the release record | Kesavan + Venkatesh | 30 d |
| 7 | Medium | SDLC | Ruleset "Main Branch Protection" exists with `enforcement: disabled` | Enable it or delete it so no dormant control remains | DevOps | 30 d |
| 8 | Medium | WISP | No periodic GitHub access-review record (domain 03) | Quarterly review of `@gg-engineering/me-health` membership, dated and retained | Eng Manager | 60 d |
| 9 | Medium | WISP | Cleartext-HTTP ATS exception for dev IP `49.207.187.28` ships in the production `Info.plist` | Move the exception to the Dev-only config so the App Store build carries no ATS exception | Kesavan | 30 d |
| 10 | Low | WISP | `LoggerService` has no redaction layer (preventive) | Allow-list or redaction step inside `LoggerService.log()` so PHI cannot reach disk/server by accident | Kesavan | 60 d |
| 11 | Low | SDLC | `CHANGELOG.md` stops at 5.0.2; 5.0.3 shipped and tagged | Add 5.0.3 and 5.0.4 entries | Kesavan | 60 d |
| 12 | Low | SDLC | Dependabot targets `dev` (Android) / default `main` (iOS); neither targets `develop` | `target-branch: "develop"` for both ecosystems | Kesavan | 60 d |
| 13 | Low | SDLC | Dependabot `commit-message.prefix` uses legacy `MA-3424`/`MA-3589` | Update to a current `MOB-` ID or drop the prefix | Kesavan | 60 d |
| 14 | Low | SDLC | Lefthook gates degrade to warnings when a binary is missing | Document `brew install lefthook detekt swiftlint gitleaks && lefthook install` as required onboarding in `CONTRIBUTING.md` | Kesavan | 60 d |

**Items 2, 4, 6 are documentation, not engineering** — they are the shortest path from 3 to 4 on both
scores, and all three are owned by the Mobile Lead.

---

## 4. How to verify

Re-runnable checks. Run from the repo root on the branch being audited.

### Automated SDLC audit
```bash
# Full 6-category audit via the sdlc plugin
/sdlc:sdlc-audit
```

### Repository controls
```bash
# Required files
for f in README.md CODEOWNERS .gitignore LICENSE .editorconfig SECURITY.md .gitleaks.toml; do
  [ -e "$f" ] && echo "PRESENT $f" || echo "MISSING $f"
done

# CODEOWNERS must reference a team, not individuals
grep -E '@[a-z0-9-]+/' CODEOWNERS
```

### Branch protection (needs an **admin**-scoped token)
```bash
gh api repos/gg-engineering/meApp/branches/main/protection
gh api repos/gg-engineering/meApp/branches/develop/protection
gh api repos/gg-engineering/meApp/rulesets            # check enforcement != "disabled"
```
Expect: `required_approving_review_count >= 1`, `require_code_owner_reviews: true`,
`required_status_checks.contexts` listing the CircleCI jobs, `allow_force_pushes: false`,
`allow_deletions: false`.

### Code-review evidence
```bash
# Every merged PR should read APPROVED
gh pr list --repo gg-engineering/meApp --state merged --limit 30 \
  --json number,baseRefName,reviewDecision,author \
  --jq '.[] | "\(.number)\t\(.reviewDecision // "NONE")\t\(.author.login)"'
```

### CI gates
```bash
for k in gitleaks sonar semgrep owasp detekt swiftlint jacoco coverage; do
  printf "%-12s %s\n" "$k" "$(grep -ic "$k" .circleci/config.yml)"
done
```

### Coverage floors
```bash
grep -n -A3 'counter = "LINE"' Android/app/build.gradle.kts   # Android JaCoCo minimum
grep -n 'THRESHOLD=' .circleci/config.yml                     # iOS xccov threshold
```

### Secrets scan
```bash
brew install gitleaks     # not installed by default — the pre-commit hook silently skips without it
gitleaks dir . --no-banner
```

### WISP — app-scope controls
```bash
# 07 Network Security — no ATS exception should ship in the production build
grep -A12 NSAppTransportSecurity iOS/meApp/Resources/Info.plist
grep -n 'usesCleartextTraffic\|networkSecurityConfig' Android/app/src/main/AndroidManifest.xml

# 10 Cryptography — credentials must be in Keychain / EncryptedSharedPreferences
grep -rln 'KeychainService\|kSecClass' iOS/meApp/Data iOS/meApp/Core
grep -rln 'EncryptedSharedPreferences\|MasterKey' Android/app/src/main

# 09 Secure Development — HIPAA lint rules must be present and set to error
grep -n -A6 'no_print_or_nslog' iOS/.swiftlint.yml

# 15 Privacy — no measurement values in log statements
grep -rnE 'log\(.*\\\((weight|value|systolic|diastolic|bmi)' iOS/meApp Android/app/src/main
```

### WISP — documentation checks (manual)
| Check | Where | Pass criteria |
|---|---|---|
| Appendix B validated | [Appendix B](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1247707162/Appendix+B+Mobile+Engineering) | No cell reads "Pending owner input"; status is not "Action Required" |
| PHI-handling doc | Confluence, Mobile Engineering | Exists, names storage / logging / telemetry / retention |
| Release sign-off | [Release Sign-Off Checklist](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1286307841/Release+Sign-Off+Checklist) | A completed copy per production release, three named approvals |
| Access review | GitHub org settings | Dated quarterly review of team membership |

---

## 5. Compliance impact

| Finding | Framework touchpoint | Mitigation in place |
|---|---|---|
| #1 Android coverage below standard | SOC2 CC8.1 · HITRUST secure-SDLC testing | Ratchet is documented and monotonic; iOS meets 80% |
| #2 No PHI-handling doc | **HIPAA §164.312(a)(1), §164.312(b)** · WISP 10 + 15 | Controls exist in code (Keychain, EncryptedSharedPreferences, OS FDE) — unwritten, not absent |
| #3 Branch protection unevidenced | SOC2 CC8.1 change management | Protection confirmed on; 15/15 recent merges approved |
| #4 Appendix B unfilled | HITRUST 00 governance · SDLC Policy team appendices | Practice exists; documentation does not |
| #5 No SAST gate | SOC2 CC8.1 · HITRUST secure-SDLC | SwiftLint (HIPAA rules), detekt, OWASP, gitleaks cover static/dependency/secret analysis |
| #6 No release sign-off record | SOC2 CC8.1 approval · HIPAA §164.308(a)(8) | QA and Tech Lead approval happen in practice via Jira + PR |
| #9 ATS exception in prod build | HIPAA §164.312(e)(1) transmission security | Scoped to a single dev IP; production host stays HTTPS under full ATS |

Findings #11–#14 are hygiene with no compliance exposure.

---

## 6. Reference documents

All canonical sources this audit scores against. Confluence space **GGT** (Greater Goods Technology).

### Program front doors
| Document | Purpose |
|---|---|
| [Written Information Security Program (WISP)](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245708290/Written+Information+Security+Program+WISP) | Master security policy — the 15 domains in §2.2. Approved by James Maes (CTO), 2026-04-14, annual review |
| [Secure SDLC Program](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1476296742/Secure+SDLC+Program) | Compliance front door for engineering lifecycle controls |
| [Information Security Program](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1245249562/Information+Security+Program) | Umbrella program — HIPAA / SOC2 / HITRUST |
| [Compliance Program Index](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1237712898/Compliance+Program+Index) | Front door for the whole GGT compliance program |
| [HITRUST Compliance Program](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1476231198/HITRUST+Compliance+Program) | HITRUST scope, cadence, and control mapping |

### SDLC canonical standards
| Document | Governs |
|---|---|
| [Software Development Lifecycle (SDLC) Policy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1244233748/Software+Development+Lifecycle+SDLC+Policy) | Governing policy and exception handling |
| [**Appendix B: Mobile Engineering**](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1247707162/Appendix+B+Mobile+Engineering) | **Our team appendix — finding #4** |
| [Git Repository Standards](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1246888028/Git+Repository+Standards) | Branch protection, PR requirements, repo audit |
| [CI/CD Standards](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1246888081/CI+CD+Standards) | Required pipeline gates |
| [Testing Standards](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1248624721/Testing+Standards) | Coverage thresholds, QA ownership, critical journeys |
| [Secure Coding Standards](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1246953506/Secure+Coding+Standards) | Secure coding, PHI handling, security-review triggers |
| [Versioning Policy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1323958382/Versioning+Policy) | Versioning and release labelling |
| [Release Sign-Off Checklist](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1286307841/Release+Sign-Off+Checklist) | Per-release approvals — finding #6 |
| [Audit Evidence Matrix](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1286176769/Audit+Evidence+Matrix) | Control-to-evidence traceability and retention |
| [Engineering Standards](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1479639041/Engineering+Standards) | Repository, CI/CD, coding, testing, deployment standards |
| [Delivery Checklists and Release Controls](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1478852610/Delivery+Checklists+and+Release+Controls) | Sprint evidence, release readiness, sign-off |
| [SDLC Governance and Policy](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1479606273/SDLC+Governance+and+Policy) | Policy, FAQ, ownership, control mapping |
| [SDLC Policy FAQ](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1284702239/SDLC+Policy+FAQ) | Interpretation — incl. why placeholders are not evidence |

### Mobile team pages
| Document | Purpose |
|---|---|
| [Mobile Engineering](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1438121987/Mobile+Engineering) | Mobile team home |
| [Releases and QA](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1475182614/Releases+and+QA) | Deployment workflows, release testing, QA checklists |
| [Security, Compliance & Trust](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1438253057/Security+Compliance+Trust) | Where WISP / SDLC / evidence operations live |

---

## 7. Change log

| Date | Change |
|---|---|
| 2026-07-27 | Renamed from `SDLC_AUDIT_2026-07-22.md` to an undated maintained doc (per `docs/rules/REPO_LAYOUT.md`). Added the 1–5 scoring, the WISP section, the pending register, and §4 verification commands. **Corrected the Android coverage claim from 80% to the actual 50% floor.** Added findings #1, #2, #4, #6, #7, #8, #9, #10, #11, #14. Overall SDLC status revised from "PASS with findings" to **3 — Partially Compliant**, reflecting the evidence gaps rather than a change in engineering practice. |
| 2026-07-22 | Initial SDLC audit via the `sdlc` plugin — 1 Medium + 2 Low findings. |
