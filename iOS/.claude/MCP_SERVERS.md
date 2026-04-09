# MCP Servers Setup Guide

This guide enables recommended MCP servers for enhanced Claude Code capabilities in the meApp iOS project.

## Recommended MCP Servers

### 1. **Atlassian MCP** (High Priority)
**Why**: Direct Jira (MA project) and Confluence integration for ticket lookup, work logging, and design doc references.

**Setup**:
```bash
# In Claude Code, run:
claude mcp add atlassian
```

**Capabilities**:
- Fetch Jira tickets without leaving Claude
- Search Confluence docs while coding
- Log work time on issues
- Link PRs to tickets automatically

**Usage**:
```
"Fetch MA-3619 details"
"Search confluence for HealthKit integration patterns"
"Log 2 hours on this ticket"
```

---

### 2. **GitHub MCP** (High Priority)
**Why**: Check CI/CD status, verify branch protection, and manage PRs from within Claude.

**Setup**:
```bash
claude mcp add github
```

**Capabilities**:
- Check GitHub Actions workflow status
- List open PRs and issues
- Query branch protection rules
- Create/update pull requests

**Usage**:
```
"Check if main branch protection requires PR reviews"
"List failing GitHub Actions on this commit"
"Create a PR linking to MA-3619"
```

---

### 3. **Figma MCP** (Medium Priority)
**Why**: Read design specs and Code Connect mappings directly from Figma files during implementation.

**Setup**:
```bash
claude mcp add figma
```

**Capabilities**:
- Get design context from Figma URLs
- Query component Code Connect mappings
- Extract design tokens
- Generate screenshots for documentation

**Usage**:
```
"Get the design context for https://figma.com/design/..."
"Show me the Code Connect mapping for this component"
"Extract color tokens from the design system"
```

---

### 4. **Context7 MCP** (Medium Priority)
**Why**: Live documentation lookup for Swift, SwiftUI, and iOS frameworks without relying on training data.

**Setup**:
```bash
claude mcp add context7
```

**Capabilities**:
- Query Swift/iOS API documentation
- Get SwiftUI view examples
- Find framework-specific best practices
- Search Apple SDK docs

**Usage**:
```
"What's the latest way to handle async/await in SwiftUI?"
"Show me SwiftData query examples"
"How do I properly handle Combine subscriptions?"
```

---

## Installation Steps

### One-time Setup

1. **Authenticate with each service** (prompted on first use):
   ```bash
   # Atlassian - uses OAuth
   claude mcp add atlassian
   # → Opens browser for Jira/Confluence login
   
   # GitHub - uses personal access token
   claude mcp add github
   # → Prompts for GitHub PAT or opens OAuth flow
   
   # Figma - uses personal access token
   claude mcp add figma
   # → Prompts for Figma API token
   
   # Context7 - uses API key
   claude mcp add context7
   # → Automatic setup (no auth required)
   ```

2. **Verify installation**:
   ```bash
   claude mcp list
   # Should show: atlassian, github, figma, context7
   ```

3. **Save to project settings** (optional, for team sharing):
   Add to `.claude/settings.json`:
   ```json
   {
     "enabledPlugins": {
       "atlassian-tools@official": true,
       "github-tools@official": true,
       "figma-tools@official": true,
       "documentation-tools@official": true
     }
   }
   ```

---

## Token/Credential Management

### Atlassian
- **Method**: OAuth 2.0 (browser login)
- **Scope**: Jira (greatergoods.atlassian.net), Confluence
- **Revoke**: Atlassian Account Settings → Apps & Authorizations

### GitHub
- **Method**: Personal Access Token (PAT) or OAuth
- **Scope**: `repo` (public/private), `workflow`, `read:org`
- **Create**: GitHub → Settings → Developer Settings → Personal Access Tokens
- **Revoke**: GitHub Settings → Developer Settings → Tokens → Delete

### Figma
- **Method**: Personal Access Token
- **Scope**: File read access
- **Create**: Figma → Account Settings → Personal Access Tokens
- **Revoke**: Figma Account → Personal Access Tokens → Delete

### Context7
- **Method**: Automatic (no credentials)
- **Scope**: Read-only documentation access
- **Cost**: Free tier available

---

## Usage Patterns

### During Implementation

```
Task: Implement a new Dashboard card showing daily trends

1. "Fetch MA-3620 to understand requirements"
   → Atlassian: Gets ticket details, acceptance criteria

2. "Get design context for the dashboard mockup"
   → Figma: Extracts colors, spacing, typography

3. "How do I create an animated SwiftUI chart?"
   → Context7: SwiftUI animation examples

4. "Create a PR linking to MA-3620"
   → GitHub: Opens PR with ticket link
```

### During Code Review

```
1. "Check if GitHub Actions are passing on main"
   → GitHub: Verifies CI status

2. "Search Confluence for our accessibility guidelines"
   → Atlassian: Finds docs on VoiceOver support

3. "What's the latest on async/await patterns?"
   → Context7: Gets Swift concurrency best practices
```

---

## Troubleshooting

### "MCP server not found"
**Solution**: Run `claude mcp add {server}` to install

### "Authentication failed"
**Solution**: 
- Atlassian: Ensure Jira email matches Atlassian account
- GitHub: Verify PAT has `repo` scope
- Figma: Check token hasn't expired in Figma settings

### "No results for query"
**Solution**:
- Context7: Try broader search terms or different wording
- Figma: Ensure you have access to the file/project
- Atlassian: Check project key is correct (MA-xxxx)

---

## Performance Tips

- **Context7**: Cache Swift/iOS docs locally with `--cache` flag
- **Figma**: Use specific file URLs instead of searching
- **Atlassian**: Narrow search to `project:MA` for faster results
- **GitHub**: Use branch name to filter, avoid searching all repos

---

## Team Sharing

To ensure your team has the same MCP setup:

1. **Commit this file** to the repository:
   ```bash
   git add .claude/MCP_SERVERS.md
   git commit -m "MA-XXXX Document MCP server setup for team"
   ```

2. **Share authentication approach**:
   - Use org-level tokens (preferred) for Jira/GitHub
   - Each developer creates their own Figma token
   - Context7 requires no setup

3. **Verify in CI** (optional):
   ```bash
   # In GitHub Actions
   - run: claude mcp list | grep -E "atlassian|github|figma|context7"
   ```

---

## References

- [Claude MCP Documentation](https://modelcontextprotocol.io/)
- [Atlassian OAuth](https://developer.atlassian.com/cloud/jira/platform/oauth-2-3lo-user-instructions/)
- [GitHub PAT](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
- [Figma API](https://www.figma.com/developers/api#Authentication)
- [Swift Documentation](https://www.swift.org/documentation/)
