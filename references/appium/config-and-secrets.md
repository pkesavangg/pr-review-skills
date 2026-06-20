# Appium Config, Capabilities & Secrets — keep the harness clean and safe

The wdio config and capabilities are code too, and they're a common home for committed secrets, environment-specific values, and security-relaxing flags. Severity uses the orchestrator's taxonomy.

---

## P0 — Hardcoded credentials / secrets / PII committed

Real passwords, API tokens, signing material, or production user emails in specs, page objects, or config leak into git history permanently — a security incident, not a style issue.

```typescript
await LoginPage.login("real.user@greatergoods.com", "Sup3rSecret!");   // committed credential
const apiToken = "ghp_xxxxxxxxxxxx";                                    // secret in test code
```

**Sniff.** On `+` lines in any changed `.ts`/config file: things that look like passwords, `token`/`secret`/`apiKey`/`password` assigned a literal, private keys, or real-looking user emails. Cross-check against the security reference (`secrets-and-storage.md`) too.

**Fix.** Pull secrets from environment variables / a secrets manager / CI secrets (`process.env.TEST_USER`, `process.env.TEST_PASS`), never literals. Use dedicated **non-production** test accounts. If a secret was already committed, it must be rotated, not just deleted.

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

## Nit — Magic infra numbers in config

`port: 4723`, `connectionRetryTimeout: 120000`, `waitforTimeout: 20000` as bare literals.

**Fix.** Name them or pull from a config/env layer so they're tunable and self-documenting.
