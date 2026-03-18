---
name: read-figma
description: Extract and summarize design context from a Figma URL found in a Jira ticket description or passed directly. Use when a Figma link is present in a ticket, when the user says "read the figma", "get the design", or "what does the design look like". Returns a structured Design Summary with screen names, components, text strings, and design tokens. Called automatically by /work-ticket Step 1.7.
---

# Skill: Read Figma Design

## Purpose

Extract and summarize design context from a Figma URL. Called with a Figma URL (or Jira ticket body containing one). Returns a structured **Design Summary** for use in PRD generation and implementation planning.

## Input

`$ARGUMENTS` — Either:
- A direct Figma URL: `https://www.figma.com/design/{FILE_KEY}/...?node-id={NODE_ID}`
- Raw Jira ticket description text (skill will extract the URL)

---

## Execution

### Step 1 — Extract Figma URL

If `$ARGUMENTS` is ticket body text, extract the first Figma URL using this pattern:
```
https://www\.figma\.com/(design|file|proto)/[A-Za-z0-9]+[^\s"')>]*
```

If no URL found, output: `No Figma link found — skipping design read.` and exit.

### Step 2 — Parse URL Components

From the URL extract:
- **File key**: the alphanumeric segment after `/design/` or `/file/`
- **Node ID**: the `node-id` query parameter (URL-decoded: replace `-` with `:`)

Example:
```
https://www.figma.com/design/ABC123XYZ/App-Screens?node-id=42-1234
→ file_key = ABC123XYZ
→ node_id  = 42:1234
```

### Step 3 — Fetch Design Data via MCP

Use the Figma MCP tools to fetch design data.

**If a node ID was found**, fetch that specific node/frame:
- Use the MCP `get_node` (or equivalent) tool with the file key and node ID
- This returns the frame's component tree, text content, styles, and layout

**If no node ID**, fetch the file overview:
- Use the MCP `get_file` tool with the file key
- Retrieve the page list and top-level frame names

### Step 4 — Build Design Summary

Output a structured **Design Summary** in this format:

```
## Figma Design Summary

**File**: {file name}
**Frame / Screen**: {node name or "full file"}
**Figma URL**: {original URL}

### Screens / Sections
- List of top-level frames or sections visible in this node

### Key UI Components
- Component names present (e.g. Button/Primary, Card/WeightEntry, NavBar/Back)
- Note any that map to existing iOS components in `meApp/Features/Common/`

### Text Content
- All visible text strings (labels, headings, placeholders, CTAs)
- These can be used to populate `Strings/` files

### Design Tokens Used
- Colors referenced (map to Theme/ tokens where possible)
- Typography styles (map to Theme/Typography)
- Spacing/padding values notable for layout

### Interaction Notes
- Any flow annotations, variant states (empty, loading, error), or conditional UI noted in the design
```

### Step 5 — Fallback (MCP Unavailable)

If the Figma MCP is not configured or returns an error, fall back to the Figma REST API:

```
GET https://api.figma.com/v1/files/{FILE_KEY}/nodes?ids={NODE_ID}
Header: X-Figma-Token: {FIGMA_API_KEY from environment}
```

Use `WebFetch` to call this endpoint. Parse the JSON response to extract:
- `name` from the node
- `children` array — iterate for text nodes (`type: TEXT`) and component instances (`type: INSTANCE`)
- `fills` for color information
- `style` for typography references

If the token is unavailable, output:
```
⚠️ Figma MCP not configured and no FIGMA_API_KEY found.
To enable Figma design reading, add the Framelink MCP server to ~/.claude/settings.json:

"mcpServers": {
  "figma": {
    "command": "npx",
    "args": ["-y", "@framelink/figma-mcp", "--stdio"],
    "env": { "FIGMA_API_KEY": "your-personal-access-token" }
  }
}

Get your token: Figma → Settings → Security → Personal Access Tokens
Required scope: File content (Read)
```

---

## Output

The Design Summary is returned inline for the calling command to use. No files are written by this skill.
