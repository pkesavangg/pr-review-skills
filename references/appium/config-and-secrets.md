# Appium Config, Capabilities & Secrets — keep the harness clean and safe

The wdio config and capabilities are code too, and they're a common home for committed secrets, environment-specific values, and security-relaxing flags. Severity uses the orchestrator's taxonomy.

---

## P0 — Hardcoded credentials / secrets / PII committed

Real passwords, API tokens, signing material, or production user emails in specs, page objects, or config leak into git history permanently — a security incident, not a style issue.

```typescript
await LoginPage.login("real.user@greatergoods.com", "Sup3rSecret!");   // committed credential
const apiToken = "ghp_xxxxxxxxxxxx";                                    // secret in test code
```

**Sniff.** On `+` lines in any changed `.ts`/config file — **including `test/data/*.data.ts` and `test/data/constants.ts`** (fixtures are a common home for committed passwords): things that look like passwords, `token`/`secret`/`apiKey`/`password` assigned a literal, private keys, or real-looking user emails. Cross-check against the security reference (`secrets-and-storage.md`) too.

**Fix.** Pull secrets from environment variables / a secrets manager / CI secrets (`process.env.TEST_USER`, `process.env.TEST_PASS`), never literals — this repo already does this correctly in places (e.g. `login.data.ts` uses `process.env.TEST_PASSWORD_2`). Follow that pattern for all credentials. Use dedicated **non-production** test accounts. If a secret was already committed, it must be rotated, not just deleted.

**Real credential vs. throwaway fixture.** A synthetic value for a *local/emulator* account that never authenticates against production (e.g. a dummy signup password on a disposable account) is lower-risk than a real user's live credential — but the safe default is still env-sourcing, and a value that could log into a real backend is always **P0**. When in doubt, treat it as real and flag it.

---

## P1 — `relaxedSecurity: true` on the Appium service

`relaxedSecurity` enables powerful, potentially dangerous server features (shell exec, file ops). Fine for a trusted local run; risky on shared/CI hosts and worth a conscious decision.

```typescript
services: [["appium", { command: "appium", args: { relaxedSecurity: true } }]];
```

**Sniff.** `relaxedSecurity: true`, `--allow-insecure`, `--relaxed-security` in config.

**Fix.** Enable only the specific insecure features you need (`allowInsecure: ['adb_shell']`) rather than blanket relaxed security, and only in environments where it's acceptable. Document why.

---

## P1 — Environment-specific / absolute path committed

Hardcoded machine paths or environment values make the suite non-portable and break on CI / other devs' machines.

```typescript
"appium:app": "/Users/kesavan/builds/meApp.apk",   // only exists on one machine
"appium:deviceName": "Pixel_8a_API_31",            // pins one local emulator
```

**Sniff.** Absolute filesystem paths, specific local device/emulator names, or localhost ports hardcoded in capabilities.

**Fix.** Derive from `process.cwd()` / env vars (`process.env.APP_PATH`, `process.env.DEVICE_NAME`) with sane defaults, so the same config runs locally and on CI.

---

## P2 — Capabilities not parameterized for platform/device matrix

Device name, platform version, and app path baked into the config prevent running the same suite across the device matrix CI needs.

**Fix.** Make the matrix-varying capabilities configurable via env/CLI; keep only true constants inline.

---

## P2 — Permission / reset capabilities chosen implicitly

`autoGrantPermissions: true`, `noReset`, `fullReset` materially change test behavior and isolation but are easy to set without thinking through the consequences.

**Sniff.** Changes to `autoGrantPermissions`, `noReset`, `fullReset`, `appium:permissions` in a PR.

**Fix.** Confirm the choice matches the test intent (e.g. `noReset: true` deliberately preserves session — does the suite rely on clean state elsewhere?) and note it in the PR.

---

## P2 — Test data inline instead of fixtures / env

Usernames, search terms, and expected values scattered as literals across specs are hard to maintain and easy to leak.

**Fix.** Centralize test data in a fixtures module or env, separated from test logic.

---

## P2 — Lint gate can't mechanically catch the P0/P1 bug classes

Two of the highest-severity Appium rules — **missing `await` on a WDIO command** (P0, `typescript-and-async.md`) and **`pause` as a wait substitute** (P1, `waits-and-synchronization.md`) — are mechanically catchable, but only if the lint config is wired for it. This repo runs ESLint v9 (`eslint.config.mjs`) with `typescript-eslint`'s **`recommended`** preset, which does **not** enable type-aware promise rules — so a floating (un-awaited) promise ships unflagged today (git history confirms real "add missing inner await" fixes). When a PR touches the ESLint/CI config, check whether these are on; when a PR *adds a floating-promise or pause bug*, note that the gate would have caught it if enabled.

**Sniff.** In `eslint.config.*` / `package.json`: absence of `@typescript-eslint/no-floating-promises` (needs `recommendedTypeChecked` or `strictTypeChecked` + `parserOptions.projectService`) and/or `eslint-plugin-wdio`'s recommended config.

**Fix.** Recommend (don't block on) enabling a mechanical backstop in the meApp repo's CI:

- **`eslint-plugin-wdio`** (official WebdriverIO plugin) — its recommended config adds `wdio/no-pause` (bans `pause(<number>)`) and, when TypeScript is detected, `wdio/no-floating-promise`. Flat-config ready.
- **`typescript-eslint` type-checked preset** — upgrade `configs.recommended` → `configs.recommendedTypeChecked` so `no-floating-promises` / `no-misused-promises` / `await-thenable` run.

With those on, the reviewer stops being the only line of defense against a class of bug that a linter catches for free. Note it in the Step 5 summary rather than blocking a test PR on a lint-config change.

---

## Nit — Magic infra numbers in config

`port: 4723`, `connectionRetryTimeout: 120000`, `waitforTimeout: 20000` as bare literals.

**Fix.** Name them or pull from a config/env layer so they're tunable and self-documenting.
