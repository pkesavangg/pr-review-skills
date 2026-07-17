# iOS Release Notes Generator

## Quick Start
Drag and drop this file to the chat and provide the current date to automatically generate release notes.

---

## Instructions

### 1. Get Version and Build Number
Location: `iOS/meApp.xcodeproj/project.pbxproj`
- Look for `MARKETING_VERSION` (e.g., 1.0.0)
- Look for `CURRENT_PROJECT_VERSION` (e.g., 19)

### 2. Get Git Log for iOS Changes
Use the previous week starting from Monday:
```bash
# If today is Friday Oct 10, 2025, start date is Monday Sep 29, 2025
git log --since="YYYY-MM-DD" --until="YYYY-MM-DD" --no-merges --format="%s" -- iOS/
```

### 3. Summarize Changes

---

## Release Notes Format Template

```markdown
MeApp iOS v{VERSION} (Build {BUILD_NUMBER}) is now available on TestFlight.

Please uninstall any previous TestFlight builds before installing this version to avoid conflicts.

## Updates

### [Dynamic Module Name]
**[Dynamic Subtitle Based on Content]:**
- [Grouped related changes]
- [Grouped related changes]

**[Another Dynamic Subtitle if Applicable]:**
- [Grouped related changes]

### [Another Dynamic Module Name]
- [Changes that don't need further grouping]

[Continue with modules and subtitles based on actual git log content]
```

**Note:** Sections and subtitles should be created dynamically based on what's actually in the git log. Don't force content into predefined categories.

---

## How to Analyze Git Log for Dynamic Categorization

### Step 1: Read Through All Commits
- Review all commits in the date range
- Look for recurring themes and patterns
- Identify which modules/features were modified

### Step 2: Identify Major Themes
- Group commits by the area they affect (Dashboard, Scale, Auth, etc.)
- Look for sub-themes within each area (e.g., within Dashboard: graphs, metrics, goals)
- Note if a theme has enough items (3+) to warrant a subtitle

### Step 3: Create Section Structure
- **Main sections** should represent major modules or features
- **Subtitles** should only be added when there are 3+ related items
- If a section has fewer than 3 items total, list them directly without subtitles

### Step 4: Prioritize by Impact
- Put the most significant changes first
- Group fixes and minor improvements toward the end

### Example Analysis:
```
Commits mentioning "Dashboard", "Graph", "Y-axis", "metrics" → Dashboard Module section
  - Many graph-related → "Graph Enhancements" subtitle
  - Multiple metric items → "Metrics & Goals" subtitle
  
Commits mentioning "Bluetooth", "Wi-Fi", "Scale" → Scale Module section
  - Mix of setup and connection → Could use subtitles or list directly
  
Few commits about "Login", "Sign-up" → Authentication section
  - Only 2-3 items → No subtitles needed, list directly
```

---

## Guidelines for Summarizing

### DO:
- ✅ **Analyze the git log first** to identify natural groupings and themes
- ✅ **Create sections dynamically** based on what modules/features were actually changed
- ✅ **Create subtitles dynamically** only when there are multiple related changes within a section
- ✅ Group related changes under logical sections
- ✅ Use user-friendly, non-technical language
- ✅ Focus on what changed, not how it changed
- ✅ Avoid mentioning specific file names or technical implementation details
- ✅ Combine similar commits into single bullet points
- ✅ Use action words: "Improved", "Enhanced", "Fixed", "Added", "Updated"
- ✅ If a module only has 1-3 changes, list them directly without subtitles

### DON'T:
- ❌ Use predefined static categories - adapt to actual content
- ❌ Create empty sections or force changes into categories where they don't fit
- ❌ Add subtitles when there are too few items to justify grouping
- ❌ Mention class names, file names, or code structure
- ❌ Use technical jargon (e.g., "refactored BaseGraphView")
- ❌ List every single commit separately
- ❌ Include internal ticket numbers as main content (MA-XXXX is okay to reference but focus on the change)
- ❌ Be too granular with technical details

---

## Example Transformations

### Technical Commit → User-Friendly Note

**Before:**
```
MA-1296 Update DashboardGraphManager to adjust buffer time for date calculations
```

**After:**
```
Improved date calculation accuracy for midnight-local summaries
```

---

**Before:**
```
feat: enhance AlertModifier to validate email input
```

**After:**
```
Added email validation with specific error messaging
```

---

**Before:**
```
MA-1265 Refactor AccountService to take immutable snapshots of offline account values
```

**After:**
```
Improved data integrity during account sync operations
```

---

## Example Module Patterns (Reference Only - Not Required)

**These are common patterns you might see, but always create sections based on actual content:**

**Dashboard-related changes might include:**
- Graph improvements (axes, labels, animations, caching)
- Metrics display and customization
- Goals and streaks
- Layout and responsiveness
- Tab navigation

**Scale-related changes might include:**
- Bluetooth connectivity
- Wi-Fi setup and connection
- Scale pairing and management
- Device synchronization
- Setup flows

**Account/Data-related changes might include:**
- Data synchronization
- Account switching
- Multi-account support
- Data migration
- Cache management

**Auth-related changes might include:**
- Login/logout flows
- Sign-up validation
- Password reset
- Email verification
- Error handling

**UI/UX changes might include:**
- Navigation flows
- Form interactions
- Modal presentations
- Animations
- Visual consistency
- Accessibility

**Performance changes might include:**
- Load time improvements
- Memory optimization
- Crash fixes
- Background processing
- Error handling

**Important:** Only create sections and subtitles for what's actually in the git log. If there are no scale changes, don't include a Scale section.

---

## Usage

1. **Drag and drop this file** into your AI chat
2. **Provide the date** (e.g., "Today is October 10, 2025")
3. **AI will:**
   - Calculate the previous week's start date (Monday)
   - Extract version and build number from project.pbxproj
   - Get iOS git log for that date range
   - Analyze commits to identify themes and groupings
   - Create dynamic sections and subtitles based on actual content
   - Generate formatted release notes in user-friendly language

---

## File Location
`iOS/docs/RELEASE_NOTES_GENERATOR.md`

Keep this file updated if the format or process changes.

