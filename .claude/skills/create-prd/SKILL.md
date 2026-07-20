---
name: create-prd
description: Generate a PRD as a markdown plan file before starting implementation. Use when the user says "create a PRD", "plan this ticket", "write up a plan for", or before any non-trivial feature or bug fix. Also runs as part of /work-ticket Step 3.
---

Generate a Product Requirements Document (PRD) / implementation plan as a markdown file.

The Jira issue ID or task description is: $ARGUMENTS

---

## Instructions

### 1 — Fetch Ticket Details

If `$ARGUMENTS` is a Jira issue ID (e.g. `MOB-3319`):
- Call `getAccessibleAtlassianResources` to retrieve the `cloudId`
- Call `getJiraIssue` with `cloudId` and the issue ID
- Extract: `ISSUE_ID`, `ISSUE_TITLE`, `ISSUE_TYPE`, `ISSUE_DESCRIPTION`, `ACCEPTANCE_CRITERIA`

If `$ARGUMENTS` is a plain description:
- Set `ISSUE_ID = "LOCAL"`, `ISSUE_TITLE = $ARGUMENTS`, `ISSUE_TYPE = "Task"`

---

### 2 — Explore the Codebase (max 8 file reads)

Search and read in parallel — stop at 8 total file reads:

```
rg -l "{keyword}" meApp -g '*.swift'
Glob pattern: meApp/Features/{Feature}/**/*
Glob pattern: meAppTests/Features/{Feature}/**/*
```

Use the Grep tool for keyword search and the Glob tool for directory listing — do not use `find`.

Read the 2–3 most relevant source files to understand current patterns. Note what already exists vs what needs to be created.

---

### 3 — Determine Implementation Approach

Based on ticket type:
- **New feature**: affected layers, DI registrations needed, new endpoints needed
- **Bug fix**: root cause area, files containing the faulty code
- **Test additions**: files needing tests, which mocks exist vs need generation

**Documentation impact:** for each file in *Files to Create/Modify/Delete*, check it against the
source→doc map in `scripts/docs-freshness-check.sh` (`doc_for()`) and the Confluence map in
`docs/confluence.md`. List every maintained doc / Confluence page the change will outdate in the
PRD's **Documentation Impact** table — or tick "No documentation impact" if none match.

---

### 4 — Write the PRD File

Slugify the title (lowercase, hyphens, no special chars). Write to:
```
docs/plans/{ISSUE_ID}-{slugified-title}.md
```

Use the template at `docs/templates/prd-template.md`. Fill every section from the Jira ticket and codebase findings.

---

### 5 — Confirm Output

```
PRD created: docs/plans/{ISSUE_ID}-{slugified-title}.md

Key decisions captured:
- Affected layers: {list}
- Files to create: {count}
- Files to modify: {count}
- New mocks needed: {count}
- Test files needed: {count}
- Docs / Confluence impacted: {list or "none"}
```
