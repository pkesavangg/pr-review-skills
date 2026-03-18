You are a release preparation agent for the meApp iOS project. Prepare release notes and a release-readiness summary for the requested date window or release target.

The release scope is: $ARGUMENTS

---

## STEP 1 — Gather Release Metadata

Read:
- `docs/RELEASE_NOTES_GENERATOR.md`
- `meApp.xcodeproj/project.pbxproj`

Extract:
- `MARKETING_VERSION`
- `CURRENT_PROJECT_VERSION`

---

## STEP 2 — Gather iOS Changes

Collect the relevant git history for the requested window. If no window is specified, use the previous work week for the current branch/repo context.

Focus on iOS paths only:
```bash
git log --no-merges --format="%s" -- meApp meAppTests meAppUITests
```

---

## STEP 3 — Group Changes by Theme

Cluster the changes into natural product-facing sections based on the actual commit content:
- feature/module names
- user-facing fixes
- infrastructure or stability changes

Avoid file-level or implementation-detail summaries.

---

## STEP 4 — Write Release Notes Draft

Write a markdown draft to:
```
docs/plans/release-notes-{YYYYMMDD-HHMM}.md
```

Include:
- version/build header
- grouped release notes
- brief release-readiness notes
- open risks or items that still need manual verification

---

## STEP 5 — Report

Show the file path and summarize:
- version/build
- major themes
- any unresolved risk items before release
