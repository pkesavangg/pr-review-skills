---
name: drawable-scan
description: Audit all Android drawable and raw resources interactively — find duplicates, unused files, and AppIcons.kt naming violations. Presents one issue at a time and resolves them with your input. Usage: /drawable-scan
---

<objective>
Audit the entire Android res/drawable/ and res/raw/ directory for:
1. Duplicate files (visually identical icons with different names)
2. Unused files (not referenced in AppIcons.kt or anywhere in code)
3. Naming violations in AppIcons.kt (camelCase/Under_Score → PascalCase)

Presents issues one at a time. User decides action for each. Persists state so audit
can be resumed if interrupted. Runs build verification after all mutations.
</objective>

<quick_start>
1. Read `Android/.claude/skills/drawable-shared-context.md` for paths and naming rules.
2. Check for existing audit state file (offer to resume).
3. Collect all drawable + raw files.
4. Parse AppIcons.kt to build registry.
5. Single-pass grep for all code references.
6. Find duplicates (XML hash + PNG PHASH).
7. Find unused (4-bucket classification).
8. Find naming violations (12 known + pattern scan).
9. Write all issues to state JSON file.
10. Present issues one at a time, interactively.
11. After all mutations: run ./gradlew assembleDebug.
12. Print final summary.
</quick_start>

<process>

## Step 0: Read shared context

Read `Android/.claude/skills/drawable-shared-context.md` before doing anything else.

## Step 1: Check prerequisites

```bash
# imagemagick for PNG PHASH comparison
if ! which magick > /dev/null 2>&1 && ! which convert > /dev/null 2>&1; then
  echo "imagemagick is not installed. Run: brew install imagemagick"
  echo "PNG duplicate detection will be skipped."
  SKIP_PHASH=true
fi
```

## Step 2: Check for existing audit state

```bash
STATE_FILE="Android/.claude/drawable-audit-state.json"

if [ -f "$STATE_FILE" ]; then
  PENDING=$(python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
pending = [i for i in data['issues'] if i['status'] == 'pending']
print(f\"{len(pending)} pending issues from {data['generated']}\")
" "$STATE_FILE" 2>/dev/null)
  echo "Found in-progress audit: $PENDING"
  echo "(a) Resume from where I left off"
  echo "(b) Start a fresh scan (discard previous results)"
  # If (a): skip to Step 9 (interactive resolution)
  # If (b): continue with fresh scan
fi
```

## Step 3: Collect all asset files

```bash
# Collect all drawable and raw files
ALL_FILES=$(find \
  Android/app/src/main/res/drawable \
  Android/app/src/main/res/drawable-night \
  Android/app/src/main/res/raw \
  Android/app/src/main/res/raw-night \
  -type f \
  ! -name "*.DS_Store" \
  ! -name "*.9.png" \
  2>/dev/null)

echo "Found $(echo "$ALL_FILES" | wc -l | tr -d ' ') asset files"
```

## Step 4: Parse AppIcons.kt registry

Read `AppIcons.kt` and extract:

```python
import re

APPICONS = "Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt"

with open(APPICONS) as f:
    content = f.read()

# Extract val entries: group name + property name + R reference
# Pattern: val PropName = R.drawable.filename OR R.raw.filename
registered = {}  # { "filename": {"group": "Default", "property": "ArrowBack"} }
current_group = None

for line in content.split('\n'):
    group_match = re.match(r'\s+object\s+(\w+)', line)
    if group_match:
        current_group = group_match.group(1)

    val_match = re.match(r'\s+val\s+(\w+)\s*=\s*R\.(drawable|raw)\.(\w+)', line)
    if val_match and current_group:
        prop_name, r_type, filename = val_match.groups()
        registered[filename] = {
            "group": current_group,
            "property": prop_name,
            "r_type": r_type,
            "line": line.strip()
        }

    # Extract fun entries too (just for display, never modify)
    fun_match = re.match(r'\s+fun\s+(\w+)\(', line)
    if fun_match and current_group:
        # Record as function (excluded from unused analysis)
        pass
```

## Step 5: Single-pass grep for all code references

**One process — not 400+ separate greps:**

```bash
# Read all source files ONCE and capture every drawable/raw reference
grep -rEo "(R\.(drawable|raw)\.([a-zA-Z0-9_]+)|@(drawable|raw)/([a-zA-Z0-9_]+))" \
  --include="*.kt" \
  --include="*.xml" \
  Android/app/src/ \
  Android/app/src/main/res/ \
  2>/dev/null \
  | grep -oE "[a-zA-Z0-9_]+$" \
  | sort -u > /tmp/drawable_references.txt

echo "Found $(wc -l < /tmp/drawable_references.txt) unique drawable/raw references in code"
```

Load this into a set for O(1) lookup.

## Step 6: Four-bucket classification

For each file in `res/drawable/` and `res/raw/` (not night folders):

```python
filename_no_ext = os.path.splitext(os.path.basename(f))[0]

in_appicons = filename_no_ext in registered
in_code = filename_no_ext in code_references  # from /tmp/drawable_references.txt

if in_appicons and in_code:
    bucket = "HEALTHY"       # skip
elif in_appicons and not in_code:
    bucket = "REGISTERED_UNUSED"   # AppIcons.kt entry exists but never used
elif not in_appicons and in_code:
    bucket = "UNREGISTERED"  # used directly via R.drawable.* but not in AppIcons.kt
else:
    bucket = "ORPHANED"      # not in AppIcons.kt, not referenced anywhere
```

Only `REGISTERED_UNUSED` and `ORPHANED` are candidates for deletion.
`UNREGISTERED` → offer to add AppIcons.kt entry.

## Step 7: Find duplicate files

### XML duplicates (Vector Drawable)

```python
from collections import defaultdict
import hashlib, xml.etree.ElementTree as ET, re

NS = "http://schemas.android.com/apk/res/android"

def vd_hash(xml_file):
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        vp = f"{root.get(f'{{{NS}}}viewportWidth')}x{root.get(f'{{{NS}}}viewportHeight')}"
        paths = sorted([
            (
                re.sub(r'\s+', ' ', re.sub(r',', ' ', p.get(f'{{{NS}}}pathData', ''))).strip(),
                p.get(f'{{{NS}}}fillColor', ''),
                p.get(f'{{{NS}}}strokeColor', ''),
            )
            for p in root.iter(f'{{{NS}}}path')
        ])
        return hashlib.md5(str(paths + [vp]).encode()).hexdigest()
    except Exception:
        return None

hash_map = defaultdict(list)
for xml_file in xml_files:
    h = vd_hash(xml_file)
    if h:
        hash_map[h].append(xml_file)

xml_duplicates = {h: files for h, files in hash_map.items() if len(files) > 1}
```

### PNG/GIF duplicates (PHASH — O(n) hash-then-group)

Compute PHASH once per file, then group by hash — same approach as XML duplicates.

```python
import subprocess, os
from collections import defaultdict

def compute_phash(filepath, frame_suffix=""):
    """Compute perceptual hash for a single image file."""
    try:
        result = subprocess.run(
            ["magick", "identify", "-quiet", "-moments", filepath + frame_suffix],
            capture_output=True, text=True, timeout=10
        )
        # Extract PH1-PH7 perceptual hash moments and round to reduce noise
        lines = [l.strip() for l in result.stdout.split('\n') if 'PH' in l]
        # Use rounded hash string as grouping key
        return '|'.join(lines[:7]) if lines else None
    except Exception:
        return None

png_hashes = defaultdict(list)
gif_hashes = defaultdict(list)

if not SKIP_PHASH:
    for f in png_files:
        h = compute_phash(f)
        if h:
            png_hashes[h].append(f)

    for f in gif_files:
        h = compute_phash(f, "[0]")  # first frame only
        if h:
            gif_hashes[h].append(f)

png_duplicates = {h: files for h, files in png_hashes.items() if len(files) > 1}
gif_duplicates = {h: files for h, files in gif_hashes.items() if len(files) > 1}
```

## Step 8: Find naming violations in AppIcons.kt

Scan all `val` entries in AppIcons.kt for violations:

```python
VALID_PASCAL = re.compile(r'^[A-Z][A-Za-z0-9]+$')

violations = []
for filename, info in registered.items():
    prop = info["property"]
    if not VALID_PASCAL.match(prop):
        # Determine correct PascalCase version
        # Under_Score: My_Fitness_Pal → MyFitnessPal
        # camelCase: closeFilled → CloseFilled, ggLogo → GgLogo
        fixed = to_pascal_case(prop)
        violations.append({
            "group": info["group"],
            "current": prop,
            "fixed": fixed,
            "filename": filename
        })
```

**Known violations to always include** (from shared context):
- `Default.closeFilled` → `CloseFilled`
- `Default.ggLogo` → `GgLogo`
- `Default.profile` → `Profile`
- `Integrations.My_Fitness_Pal` → `MyFitnessPal`
- `Integrations.Health_Connect_Logo` → `HealthConnectLogo`
- `Integrations.Health_Connect_Off` → `HealthConnectOff`
- `Integrations.No_Permission` → `NoPermission`
- `Integrations.Full_Permission` → `FullPermission`
- `Integrations.HC_Homepage` → `HcHomepage`
- `Integrations.User_Conflict` → `UserConflict`
- `Integrations.Permission_Failed` → `PermissionFailed`
- `Setup.wifiAPModeStepOn` → `WifiApModeStepOn`

## Step 9: Write audit state file

```python
import json
from datetime import datetime

STATE_FILE = "Android/.claude/drawable-audit-state.json"
os.makedirs(os.path.dirname(STATE_FILE), exist_ok=True)

issues = []
issue_id = 1

# Duplicates
for files in xml_duplicates.values():
    issues.append({"id": issue_id, "type": "DUPLICATE_XML", "files": files, "status": "pending"})
    issue_id += 1

for dup_pair in png_gif_duplicates:
    issues.append({"id": issue_id, "type": "DUPLICATE_PNG", "files": dup_pair, "status": "pending"})
    issue_id += 1

# Unused (orphaned + registered-unused)
for f, bucket in classifications.items():
    if bucket in ("ORPHANED", "REGISTERED_UNUSED"):
        appicons_entry = registered.get(os.path.splitext(os.path.basename(f))[0])
        issues.append({
            "id": issue_id,
            "type": "UNUSED",
            "file": f,
            "bucket": bucket,
            "appicons_entry": appicons_entry,
            "status": "pending"
        })
        issue_id += 1

# Unregistered (used in code but not in AppIcons.kt)
for f, bucket in classifications.items():
    if bucket == "UNREGISTERED":
        issues.append({"id": issue_id, "type": "UNREGISTERED", "file": f, "status": "pending"})
        issue_id += 1

# Naming violations
for v in violations:
    issues.append({"id": issue_id, "type": "NAMING", **v, "status": "pending"})
    issue_id += 1

state = {"generated": datetime.now().isoformat(), "issues": issues}
with open(STATE_FILE, 'w') as f:
    json.dump(state, f, indent=2)

total = len([i for i in issues if i["status"] == "pending"])
print(f"\nAudit scan complete. Found {total} issues:")
print(f"  Duplicates: {len([i for i in issues if 'DUPLICATE' in i['type']])}")
print(f"  Unused:     {len([i for i in issues if i['type'] == 'UNUSED'])}")
print(f"  Unregistered: {len([i for i in issues if i['type'] == 'UNREGISTERED'])}")
print(f"  Naming:     {len([i for i in issues if i['type'] == 'NAMING'])}")
print(f"\nStarting interactive resolution...")
```

## Step 10: Interactive resolution (one issue at a time)

Read state file, find next `pending` issue, present it:

### DUPLICATE issue

```
Issue 3/15: DUPLICATE DRAWABLE
────────────────────────────────
  ic_house.xml and ic_home.xml appear visually identical

  Both are registered in AppIcons.kt:
    AppIcons.Default.Home = R.drawable.ic_home
    AppIcons.Default.House = R.drawable.ic_house (← possible duplicate)

  (a) Delete ic_house.xml + remove AppIcons.Default.House entry
      (update any references to AppIcons.Default.Home)
  (b) Delete ic_home.xml + remove AppIcons.Default.Home entry
      (update any references to AppIcons.Default.House)
  (c) Keep both — they are intentionally different
  (d) Skip for now
```

On choice (a) or (b):
1. Grep all usages of the property being removed: `grep -r "AppIcons.Default.House" Android/app/src/`
2. Show user the list of files that reference it
3. Update all references to point to the kept property
4. Delete the file: `rm "$FILE"`
5. Remove the `val` entry from AppIcons.kt

### UNUSED issue

```
Issue 5/15: UNUSED DRAWABLE
────────────────────────────────
  ic_old_arrow.xml

  Status: ORPHANED
  → Not in AppIcons.kt
  → Not referenced in any .kt or .xml file

  (a) Delete file
  (b) Skip — keep for now
  (c) Show me what I'd expect to use it (grep with more context)
  (d) Add to AppIcons.kt (it's used but I forgot to register it)
```

On choice (a): `rm "$FILE"`

### REGISTERED_UNUSED issue

```
Issue 7/15: REGISTERED BUT UNUSED DRAWABLE
────────────────────────────────
  ic_old_close.xml

  Status: REGISTERED_UNUSED
  → AppIcons.Default.OldClose = R.drawable.ic_old_close
  → AppIcons.Default.OldClose is never referenced in code

  (a) Delete file + remove AppIcons.Default.OldClose entry
  (b) Skip — keep for now
  (c) Show all AppIcons.Default.OldClose references (maybe it's in a string template)
```

### UNREGISTERED issue

```
Issue 9/15: USED BUT UNREGISTERED
────────────────────────────────
  bolt.xml

  Status: UNREGISTERED
  → Referenced in code as R.drawable.bolt (2 occurrences)
  → Not registered in AppIcons.kt

  (a) Add to AppIcons.kt
      → Which group? [show group menu]
      → Property name: Bolt
  (b) Skip — intentionally using R.drawable.bolt directly
```

### NAMING issue

```
Issue 11/15: NAMING VIOLATION in AppIcons.kt
────────────────────────────────
  AppIcons.Integrations.My_Fitness_Pal → should be MyFitnessPal
  AppIcons.Integrations.Health_Connect_Logo → should be HealthConnectLogo
  ... (8 total in Integrations group)

  (a) Fix all 8 Integrations violations at once
      (I'll update AppIcons.kt + all call sites automatically)
  (b) Fix only this one: My_Fitness_Pal → MyFitnessPal
  (c) Skip all naming violations
```

**For any rename operation:**
1. `grep -r "AppIcons.Integrations.My_Fitness_Pal" Android/app/src/` — find all call sites
2. Display: "Found N references in these files: [list]"
3. Update all call sites with the new property name
4. Update the `val` line in AppIcons.kt
5. Run `./gradlew :app:compileDebugKotlin` to verify
6. If compile fails: diagnose and fix

### After each issue action:

Update the issue's status in the state JSON:
```python
issue["status"] = "fixed"  # or "skipped" or "kept"
with open(STATE_FILE, 'w') as f:
    json.dump(state, f, indent=2)
```

Show progress: `Issue 11/15 resolved. 4 remaining.`

Then load and present the next `pending` issue.

## Step 11: Build verification (after all mutations)

If any issues were fixed (deleted files, renamed properties, updated references):

```bash
cd Android && ./gradlew assembleDebug 2>&1 | tail -20
```

If it fails, diagnose and fix before reporting completion.

## Step 12: Cleanup and final report

If all issues are resolved (no `pending` remaining):
```bash
rm "$STATE_FILE"
echo "Audit state file cleaned up."
```

Print final report:

```
/drawable-scan complete
────────────────────────────────────────
  Duplicates fixed:  2 pairs merged
  Unused deleted:    3 files + AppIcons entries removed
  Unregistered:      1 file added to AppIcons.kt
  Naming fixed:      12 property renames across 3 groups
  Skipped:           4 items (marked for manual review)
  Healthy:           148 drawables — correctly named, registered, and in use

Build: PASSED (./gradlew assembleDebug)
────────────────────────────────────────
Next: /drawable-import to add new assets
```

</process>

<success_criteria>
drawable-scan skill is complete when:
- [ ] Shared context read at start
- [ ] imagemagick check run; scan continues without PNG PHASH if missing
- [ ] Existing state file detected; user offered resume or fresh scan
- [ ] All drawable + raw files collected (not mipmap, not DS_Store)
- [ ] AppIcons.kt parsed: all val entries extracted with group + property + r_type
- [ ] Single-pass grep produces one reference set (not per-file greps)
- [ ] 4-bucket classification: HEALTHY / REGISTERED_UNUSED / UNREGISTERED / ORPHANED
- [ ] XML duplicate detection via VD hash-map (O(n) not O(n²))
- [ ] PNG/GIF duplicate detection via PHASH with threshold 1.0 (first frame for GIFs)
- [ ] All 12 known naming violations included in issues list
- [ ] Pattern scan catches any additional camelCase/Under_Score violations
- [ ] Full issues list written to drawable-audit-state.json before interactive loop
- [ ] Issues presented one at a time with clear options
- [ ] Blocklist respected: never offers to delete ic_launcher*, adaptive_icon*, notification_icon*
- [ ] On rename: all call sites found, updated, then AppIcons.kt entry renamed
- [ ] On delete: file removed AND AppIcons.kt entry removed together
- [ ] State JSON updated after each issue resolution
- [ ] `./gradlew assembleDebug` run after all mutations; errors diagnosed and fixed
- [ ] State file cleaned up when all issues resolved
- [ ] Final summary report printed with counts
- [ ] Never modifies fun entries in AppIcons.kt
</success_criteria>
