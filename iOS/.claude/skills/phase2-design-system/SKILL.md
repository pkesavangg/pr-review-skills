---
name: phase2-design-system
description: Locate and apply the Me.Health Mega App 2.0 (Phase 2) Figma design system in iOS code. Use when implementing or reviewing a Phase 2 screen/component, when a ticket links the Me.Health-Mega-App-2.0 Figma file, when mapping Figma tokens (color/typography/spacing/radius) to the iOS Theme/, or when asked "what does the 2.0 design look like / where is the design system / which Figma node".
---

# Phase 2 design system (Figma → iOS Theme)

## Where the 2.0 design lives

**Figma file:** `Me.Health-Mega-App-2.0` · fileKey **`k0HO1SquDGrYOcoMSbrzA0`**

| Node | nodeId | URL |
|------|--------|-----|
| Design system / tokens | `8-2145` | https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=8-2145 |
| App screens | `26501-375864` | https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-375864 |

A ticket usually links a **specific node** (`?node-id=<id>`). Always work from the ticket's node, not a guessed one.

## How to pull design context

1. Prefer the **`read-figma`** skill (repo-root) — it extracts a structured Design Summary (screens, components, text, tokens) from a Figma URL in the ticket.
2. For tokens/variables directly, use the Figma MCP tools on the node from the ticket:
   - `get_design_context` — reference code + screenshot + metadata for a node
   - `get_variable_defs` — color/spacing/typography variables (**requires the node selected in the Figma desktop app**)
   - `get_screenshot` — visual reference
3. If MCP returns "nothing selected", ask the user to select the node in Figma desktop, or fall back to the screenshot + the ticket's image attachments (`read-jira-images`).

## Map Figma tokens → iOS `Theme/` (never hardcode)

Use existing tokens; see the `theme-guide` skill for the full system. Map, don't invent:

| Figma | iOS token | File |
|-------|-----------|------|
| Color styles / variables | `MeAppTheme.colorScheme.*` (`AppColorScheme`, `ColorTokens`) | `meApp/Theme/Tokens/ColorTokens.swift`, `meApp/Theme/Color.swift` |
| Text styles | `MeAppTheme.typography.*` (`CustomTextStyle`) | `meApp/Theme/Typography.swift`, `meApp/Theme/Enums/CustomTextStyle.swift` |
| Spacing / gaps | `MeAppTheme.spacing.*` | `meApp/Theme/Tokens/Spacing.swift` |
| Corner radius | `MeAppTheme.*` radius | `meApp/Theme/Tokens/BorderRadius.swift` |
| Elevation / shadow | drop-shadow tokens | `meApp/Theme/Tokens/DropShadow.swift` |

Rules:
- If a Figma value has **no** matching token, add it to the right `Tokens/` file (light + dark), then reference it — do not inline literals in views.
- All new views must support light + dark and Dynamic Type, and ship a `#Preview` (`add-preview`) + accessibility pass (`add-accessibility`).
- Phase 2 spans Weight, Blood Pressure, and Baby — confirm which product surface a screen belongs to and reuse shared components from `Features/Common/` before building new ones.

## Related
`phase2-context` (product/API model) · `theme-guide` (token system) · `read-figma` · `read-jira-images` · `add-preview` · `add-accessibility`.
