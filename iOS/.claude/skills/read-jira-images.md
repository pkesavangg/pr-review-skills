---
name: read-jira-images
description: Download and visually analyse image attachments from a Jira issue using Claude's vision. Use when a ticket has image attachments (PNG, JPG, GIF, WebP) such as UI mockups, flow diagrams, or screenshots. Returns a structured Image Summary with component names, visible text, layout notes, and annotations. Called automatically by /work-ticket Step 1.7.
---

# Skill: Read Jira Ticket Images

## Purpose

Download and visually analyze image attachments from a Jira issue. Returns an **Image Summary** describing UI layouts, annotations, and design intent visible in the images.

## Input

`$ARGUMENTS` — The full Jira issue data object returned by `getJiraIssue` (passed from `fetch-ticket.md`).

---

## Execution

### Step 1 — Extract Image Attachments

From the issue data, read `fields.attachment`. Filter for image MIME types:
- `image/png`, `image/jpeg`, `image/jpg`, `image/gif`, `image/webp`

If no image attachments exist, output: `No image attachments found.` and exit.

For each image, note:
- `id` — attachment ID
- `filename` — file name
- `content` — direct download URL (e.g. `https://greatergoods.atlassian.net/rest/api/3/attachment/content/{id}`)
- `mimeType`

### Step 2 — Download Images to /tmp

For each image attachment, download it to `/tmp/{filename}` using the Atlassian MCP fetch tool:

**Primary: use `mcp__claude_ai_Atlassian__fetch`** with the `content` URL. This uses the authenticated Atlassian session — no credentials needed.

**Fallback: use Bash curl** if MCP fetch does not return binary content:
```bash
curl -s -L \
  -H "Authorization: Basic $(echo -n '{ATLASSIAN_EMAIL}:{ATLASSIAN_API_TOKEN}' | base64)" \
  "{content_url}" \
  -o "/tmp/{filename}"
```

Note: The curl fallback requires `ATLASSIAN_EMAIL` and `ATLASSIAN_API_TOKEN` environment variables. If not set, skip that attachment and note it in the summary.

Process a maximum of **3 images** (most tickets have 1–2). Skip duplicates by filename.

### Step 3 — Analyze Images with Vision

For each successfully downloaded image, use the `Read` tool on `/tmp/{filename}`.

Claude's vision will analyze the image. For each one, extract:

**If it looks like a UI mockup / screen design:**
- Screen name / feature area (infer from content)
- Visible UI components (inputs, buttons, labels, cards, nav bars)
- All readable text (labels, headings, placeholder text, CTAs)
- Layout structure (top-to-bottom flow)
- Any annotation arrows, callouts, or notes visible

**If it looks like a flow diagram / user journey:**
- Steps in the flow
- Decision points
- Entry and exit states

**If it looks like a screenshot / bug report image:**
- What's shown (current UI state)
- Any highlighted areas or annotations
- Visible error states or unexpected behaviour

### Step 4 — Output Image Summary

```
## Jira Image Summary

**Attachments analysed:** {count}

### Image 1: {filename}
**Type:** UI Mockup / Flow Diagram / Screenshot
**Screen/Feature:** {inferred name}

**UI Components visible:**
- {component list}

**Text content:**
- {all readable strings}

**Layout notes:**
- {top-level layout description}

**Annotations / notes in image:**
- {any callouts, arrows, written notes}

---
(repeat for each image)
```

### Step 5 — Cleanup

After analysis, remove downloaded files:
```bash
rm -f /tmp/{filename1} /tmp/{filename2} ...
```

---

## Output

The Image Summary is returned inline. No files are persisted after cleanup.
