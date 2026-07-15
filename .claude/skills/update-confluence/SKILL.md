---
name: update-confluence
description: Mirror a meApp repo change onto its Confluence hub/section pages — Claude skills added/removed, run.sh/CI/automation changes, a new feature/service/model, or any major change. Reads the target page, drafts the section edit from current repo state, shows a before/after, and writes to Confluence ONLY after you approve. Use when the user says "update confluence", "sync confluence", "reflect this on the wiki", "update the meApp confluence page", or when the docs-freshness hook prints "🌐 Also mirror this to Confluence".
---

Mirror a repo change onto the meApp Confluence hub. Confluence is a shared, hand-curated
wiki — this skill **never writes without showing you the change and getting approval first**.

The change to mirror is: $ARGUMENTS

## Update style — edit in place + a dated log row (hybrid)

Confluence versions every save (author + version message + full diff in Page History), so
**editing in place never loses history**. Follow the hybrid convention — do not append raw dated
notes at the bottom, and do not rewrite the whole page:

- **Edit the affected section in place** so the page always reflects current truth — e.g. §9
  *CI/CD & automation* for a skills / `run.sh` / CI change; §2/§3 for structure/architecture; §10
  for the doc list. This keeps the reference body trustworthy.
- **For a notable change, add ONE dated row to the page's history table** — on *meApp - Development*
  that's the §1 *Overview & history* `| Date | Milestone |` table: date + one line + PR/Jira. Skip
  this for tiny edits; Page History already covers those.
- Never rewrite the whole page when a section edit will do; touch only the affected sections.
- Always set a descriptive `versionMessage` — it is the audit trail.

## 0 — Prerequisites

- Read [`docs/confluence.md`](../../../docs/confluence.md) first — it is the source of truth for the
  page tree, page IDs, and the repo-change → page/section map.
- `cloudId` = `greatergoods.atlassian.net`.
- The Atlassian MCP tools are deferred — load them with ToolSearch before use:
  `select:mcp__claude_ai_Atlassian__atlassianUserInfo,mcp__claude_ai_Atlassian__getConfluencePage,mcp__claude_ai_Atlassian__updateConfluencePage`
- Call `atlassianUserInfo` **first** — the connector identity authors the page version.

## 1 — Pick the target page

From `$ARGUMENTS` or the recent change, map to a page using `docs/confluence.md`:

| Change | Page | Section |
|---|---|---|
| Skills/agents/commands (`.claude/**`), `run.sh`/`scripts/*.sh`, `.circleci/*`, `.lefthook.yml` | **meApp - Development** `1552482315` | CI/CD & automation |
| New feature / service / model / DI / dependency | **meApp - Development** `1552482315` | Architecture |
| Unit-test infra / coverage gate / E2E framework | **meApp - Testing** `1552678923` | — |
| Release process / deployment scripts | **meApp - Releases** `1552809985` | — |

Most code/automation changes land on **meApp - Development**. If the mapping is ambiguous, ask which page before proceeding.

## 2 — Read current repo state + the page

1. Establish what actually changed: `git diff` / `git status`, and read the matching local doc
   ([`docs/automation.md`](../../../docs/automation.md), [`iOS/architecture.md`](../../../iOS/architecture.md),
   [`docs/database-schema.md`](../../../docs/database-schema.md), …). **The local doc is the source of
   truth; Confluence mirrors it** — so bring the local doc current first (or via `/update-architecture`).
2. Fetch the page in **HTML** (round-trip safe — preserves macros, `data-local-id`s, inline comments):
   `getConfluencePage(cloudId, pageId, contentFormat="html")`. Note the current title.

## 3 — Draft the section edit

- Locate **only** the section the change affects (e.g. the *CI/CD & automation* table, the *Architecture* list).
- Edit that section within the fetched HTML. Leave every other section **byte-for-byte unchanged**,
  including existing `data-local-id` attributes. Do not reformat or "tidy" unrelated content.
- Match the page's existing structure (same table shape, heading levels, panel `data-type`).
- Never invent opaque IDs; only reuse IDs copied from the fetched HTML, and omit `data-local-id` on new nodes.

## 4 — Show before/after and get approval  ·  HARD GATE

Present to the user:
- The page + section being changed (title + id).
- A concise **before → after** of just the changed section.
- The `versionMessage` you intend to use (e.g. the `MOB-XXXX` id + a short reason).

**STOP. Do not call `updateConfluencePage` until the user approves.** If they want changes, revise and re-show. This gate is mandatory — there is no auto-write path.

## 5 — Write

`updateConfluencePage(cloudId, pageId, contentFormat="html", body=<FULL modified HTML>, versionMessage="…")`

- `body` must be the **entire** page HTML with your section edit applied — the API replaces the whole body, so a partial body would wipe the rest of the page.
- Version auto-increments; no version number needed.

## 6 — Confirm

Report: page title + URL, the section changed, the new version number, and the `versionMessage`.
If `docs/confluence.md` shows other pages are also affected by this change, offer to do them next.

## Notes

- **Never auto-write.** The Step 4 approval gate always applies.
- Keep the local doc and Confluence in the same unit of work: update the local `docs/` file first, then run this to publish upward.
- If the live page structure has drifted from `docs/confluence.md` (page renamed/moved/split),
  fix `docs/confluence.md` and the `CONF_PAGE` note in
  [`scripts/docs-freshness-check.sh`](../../../scripts/docs-freshness-check.sh) as part of the task.
