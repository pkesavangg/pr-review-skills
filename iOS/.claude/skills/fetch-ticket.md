Fetch and display full details of a Jira issue using the Atlassian MCP tools.

The Jira issue ID is: $ARGUMENTS

## Instructions

Use MCP tools only — do not use CLI or curl. Tokens are managed by the MCP server config.

1. Call `getAccessibleAtlassianResources` to retrieve the `cloudId`
2. Call `getJiraIssue` with the `cloudId` and issue ID to fetch full details
3. Display a structured summary:

---

### {ISSUE-ID} — {Title}

| Field         | Value |
|---------------|-------|
| Type          | …     |
| Status        | …     |
| Priority      | …     |
| Assignee      | …     |
| Reporter      | …     |
| Sprint        | …     |
| Story Points  | …     |

**Description:**
{full description text}

**Linked Issues:**
{list any linked issues with their IDs, types, and statuses — or "None"}

---

Return the raw issue data as well so the caller can use fields like summary, description, and issue type downstream.
