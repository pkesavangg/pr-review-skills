---
name: review-accessibility
description: Check new/changed meApp Android Compose UI for accessibility — TalkBack semantics, contentDescription, touch targets, and stable testTags. Use when reviewing UI a11y, or when the user says "accessibility review", "check TalkBack", "a11y check". Also called by /self-review and /review-pr.
---

Accessibility review of changed Composables. Read-only.

## Instructions

1. **Semantics / TalkBack** — interactive controls have a meaningful label (`contentDescription` or `semantics { }`); decorative icons are hidden (`contentDescription = null` / cleared semantics) so they aren't announced.
2. **Roles & state** — buttons expose a button role; toggles/selection expose state; headings marked where relevant.
3. **Touch targets** — interactive elements ≥ 48dp.
4. **testTags** — stable `testTag`s applied via the **central component params** (AppInput/SettingsItem/BaseModal/ModalDialog), never `.testTag()` on a call-site modifier chain (MOB-390 pattern). Needed for TalkBack QA + Appium (`testTagsAsResourceId`).
5. **Text scaling / contrast** — uses `MeAppTheme` type (scalable) and theme colors (contrast-checked), no fixed `sp` that blocks scaling.
6. Report `file:line — issue — fix`. Verdict: PASS / WARNING / FAIL. Cross-check with the A11y epic conventions (MOB-849…860).
