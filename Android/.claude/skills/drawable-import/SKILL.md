---
name: drawable-import
description: Import a local SVG, PNG, GIF, WebP, or Lottie JSON into Android res/ with auto-naming, SVG conversion via Gradle convertSvg task, duplicate detection, AppIcons.kt registration, and @PreviewTheme generation. Usage: /drawable-import <filepath>
---

<objective>
Import a drawable asset into the meApp Android project:
- Auto-fix filename to match project naming conventions
- Convert SVG → Vector Drawable XML via Gradle `convertSvg` task (uses Google's Svg2Vector — same as Android Studio's Vector Asset tool)
- Check for duplicates (same filename → replace; same visual → prompt)
- Route to correct res/ folder (drawable/ vs raw/)
- Register in AppIcons.kt under the user-selected group
- Verify build compiles
- Print a @PreviewTheme composable snippet
</objective>

<quick_start>
1. Read `Android/.claude/skills/drawable-shared-context.md` for paths, naming rules, and AppIcons.kt structure.
2. Get the file path from the skill arguments. If not provided, ask the user.
3. Run security checks (path traversal + filename allowlist).
4. Check idempotency — already imported AND registered? Stop early.
5. Auto-fix the filename, ask only if ambiguous.
6. Route by extension: .svg/.png/.webp → drawable/, .gif/.json → raw/.
7. Convert SVG via `./gradlew :app:convertSvg`. Check warnings for unsupported elements.
8. Check for duplicates against existing files.
9. Copy the file to the correct res/ folder (convertSvg handles SVGs directly).
10. Ask which AppIcons group to register in.
11. Append `val` entry to AppIcons.kt (atomic write).
12. Run `./gradlew assembleDebug`. Fix any errors.
13. Print @PreviewTheme snippet.
</quick_start>

<process>

## Step 0: Read shared context

Read `Android/.claude/skills/drawable-shared-context.md` before doing anything else.
This contains paths, naming rules, group structure, and AppIcons.kt edit rules.

## Step 1: Get file path

If the skill was invoked as `/drawable-import ./icons/my_icon.svg`, use that path.
If no path was provided, ask: "Please provide the path to the file you want to import."

## Step 2: Security checks

```bash
# Resolve absolute path (use python3 — realpath is not available on stock macOS)
RESOLVED=$(python3 -c "import os, sys; print(os.path.realpath(sys.argv[1]))" "$INPUT_PATH")
if [ ! -f "$RESOLVED" ]; then
  echo "Error: File not found: $INPUT_PATH"
  exit 1
fi

# Path traversal check — must stay within project
PROJECT_ROOT=$(python3 -c "import os; print(os.path.realpath(os.getcwd()))")
if [[ "$RESOLVED" != "$PROJECT_ROOT"* ]]; then
  echo "Error: path escapes project root"
  exit 1
fi

# Filename allowlist — only safe characters
FILENAME=$(basename "$RESOLVED")
if [[ ! "$FILENAME" =~ ^[a-zA-Z0-9_\-]+\.(svg|png|gif|webp|json)$ ]]; then
  echo "Error: filename contains invalid characters or unsupported extension"
  echo "Allowed: letters, numbers, hyphens, underscores. Extensions: svg, png, gif, webp, json"
  exit 1
fi
```

## Step 3: Check prerequisites

```bash
# No external tools needed for SVG conversion — the Gradle convertSvg task
# uses Google's Svg2Vector from the Android SDK (same as Android Studio's Vector Asset tool).
# It is already available on the AGP classpath.

# Check imagemagick (needed for PNG duplicate detection only)
if ! which magick > /dev/null 2>&1 && ! which convert > /dev/null 2>&1; then
  echo "imagemagick is not installed. Run: brew install imagemagick"
  echo "Continuing without PNG duplicate detection..."
  SKIP_PHASH=true
fi
```

## Step 4: Idempotency check

Determine the expected destination filename (apply naming fix from Step 5 mentally):

```bash
# Derive expected destination
EXT="${FILENAME##*.}"
BASENAME="${FILENAME%.*}"

# Check if file already exists in res/
DEST_DIR="Android/app/src/main/res/drawable"
[ "$EXT" = "gif" ] || [ "$EXT" = "json" ] && DEST_DIR="Android/app/src/main/res/raw"

if [ -f "$DEST_DIR/<fixed_name>.$EXT" ]; then
  # Check if also registered in AppIcons.kt
  if grep -q "R\.(drawable|raw)\.<fixed_name>" Android/app/src/main/java/.../AppIcons.kt; then
    echo "Already imported and registered as AppIcons.<Group>.<Name> — nothing to do."
    exit 0
  else
    echo "File exists in res/ but is not registered in AppIcons.kt."
    echo "Add AppIcons.kt entry only? (a) Yes  (b) Re-import everything"
  fi
fi
```

## Step 5: Auto-fix naming

Apply these transforms to the filename (without extension):

```
1. Replace hyphens with underscores: my-icon → my_icon
2. CamelCase → snake_case: HomeIcon → home_icon, WiFiMode → wi_fi_mode
3. Collapse multiple underscores: my__icon → my_icon
4. Lowercase everything
5. Determine prefix:
   - If starts with ic_ / bg_ / shape_ / img_ / error_ / wifi_ / scale_ / health_ / ap_ → keep as-is
   - If no recognized prefix → ask:
     "How should this be named?
      (a) ic_<name>  — generic UI icon
      (b) <name>     — domain asset (keep as-is after snake_case)
      (c) Custom name: [enter]"
```

Show the user the proposed fixed name:
```
Renaming: HomeIcon.svg → ic_home_icon.xml
Proceed? (a) Yes  (b) Use a different name
```

## Step 6: Determine destination

| Extension | Destination folder | R reference |
|---|---|---|
| `.svg` | `res/drawable/` (as `.xml` after conversion) | `R.drawable.*` |
| `.png` | `res/drawable/` | `R.drawable.*` |
| `.webp` | `res/drawable/` | `R.drawable.*` |
| `.gif` | `res/raw/` | `R.raw.*` |
| `.json` | `res/raw/` | `R.raw.*` |

Set `DEST_DIR` and `R_PREFIX` accordingly.

## Step 7: SVG conversion (SVG files only)

Use the Gradle `convertSvg` task which calls Google's `Svg2Vector` — the same converter
Android Studio's Vector Asset tool uses. No external npm tools needed.

**For a single SVG file:**

```bash
cd Android && ./gradlew :app:convertSvg \
  -PsvgInput="$RESOLVED" \
  -PsvgPrefix="" \
  -PsvgReplace="true" \
  2>&1
```

**For a folder of SVGs (batch import):**

```bash
cd Android && ./gradlew :app:convertSvg \
  -PsvgInput="/path/to/svg/folder" \
  -PsvgPrefix="scale_" \
  -PsvgReplace="true" \
  2>&1
```

**Task options (passed via -P flags):**
- `svgInput`   — (required) path to a single SVG file or a folder of SVGs
- `svgOutput`  — (optional) output folder, defaults to `res/drawable/`
- `svgPrefix`  — (optional) prefix to add to output filenames (e.g. `"scale_"`, `"ic_"`)
- `svgReplace` — (optional) `"true"` to delete existing `.png` with the same name (default: true)

The task auto-applies naming fixes (hyphens → underscores, spaces removed, lowercase).
It handles `xlink:href` gradient inheritance, `gradientTransform`, and other SVG features
that third-party tools like `s2v` fail on.

**Check the output for warnings and report ALL errors:**

Google's converter logs `ERROR` lines for unsupported SVG elements. These are warnings
(the conversion still succeeds) but they indicate visual elements that are **lost** in the
output. You MUST capture, parse, and report every single one.

**Step 7a: Capture conversion output**

```bash
CONVERT_OUTPUT=$(cd Android && ./gradlew :app:convertSvg \
  -PsvgInput="$RESOLVED" \
  -PsvgPrefix="" \
  -PsvgReplace="true" \
  2>&1)

# Extract all ERROR lines
ERRORS=$(echo "$CONVERT_OUTPUT" | grep "^ERROR")
ERROR_COUNT=$(echo "$ERRORS" | grep -c "ERROR" 2>/dev/null || echo "0")
```

**Step 7b: Classify each error by visual impact**

Group the raw errors into logical issues using this classification table:

| SVG Element(s) | Logical Issue | Impact Level | What Is Lost |
|----------------|--------------|-------------|-------------|
| `<mask>`, `Semitransparent mask` | **Alpha mask / clip** | HIGH | Content inside a masked group renders without clipping — may overflow visible boundaries |
| `<filter>`, `<feFlood>`, `<feColorMatrix>`, `<feOffset>`, `<feGaussianBlur>`, `<feComposite>`, `<feBlend>` | **Drop shadow / blur effect** | LOW | Shadow or blur around an element is lost — cosmetic only |
| `<image>` | **Embedded raster image** | HIGH | An embedded PNG/JPG inside the SVG is completely dropped |
| `<text>`, `<tspan>` | **Text element** | HIGH | Text rendered as SVG `<text>` (not paths) is dropped — missing labels/numbers |
| `<linearGradient>` / `<radialGradient>` issues | **Gradient** | MEDIUM | Gradient fill may render incorrectly or as solid color |
| `<clipPath>` | **Clip path** | HIGH | Content clipping boundary is lost |
| `<pattern>` | **Pattern fill** | MEDIUM | Repeating pattern fill is dropped |

**Step 7c: Print the full error report**

Always print a report in this format, even if there are zero errors:

```
SVG Conversion Report: <filename>
════════════════════════════════════════
Status: OK (converted successfully)
Raw errors: <N> lines from converter

Issue 1: Drop Shadow (LOW impact)
  Raw errors (7 lines):
    ERROR @ line 56: <filter> is not supported
    ERROR @ line 57: <feFlood> is not supported
    ERROR @ line 58: <feColorMatrix> is not supported
    ERROR @ line 60: <feGaussianBlur> is not supported
    ERROR @ line 61: <feComposite> is not supported
    ERROR @ line 62: <feColorMatrix> is not supported
    ERROR @ line 63: <feBlend> is not supported
  What is lost: Subtle shadow around the phone body
  Visual impact: Phone body appears flat (no shadow). Cosmetic only.

Issue 2: Alpha Mask (HIGH impact)
  Raw errors (1 line):
    ERROR @ line 20: Semitransparent mask cannot be represented by a vector drawable
  What is lost: Circular clip that crops content inside a profile circle
  Visual impact: <N> paths inside the masked group render WITHOUT clipping.
                 Content may overflow the circle boundary.

Output validation:
  File size:   <N>KB
  Path count:  <N>
  NaN values:  <N>
  Empty paths: <N>
  Gradients:   <N> preserved
════════════════════════════════════════
Summary: <N> logical issues (<H> HIGH, <M> MEDIUM, <L> LOW)
```

**Step 7d: Determine what's inside affected groups**

For HIGH-impact issues (mask, text, image), analyze the source SVG to quantify the loss:

```bash
# Count paths inside masked groups
MASK_PATHS=$(sed -n '/<g mask=/,/<\/g>/p' "$RESOLVED" | grep -c '<path')
echo "Paths affected by lost mask: $MASK_PATHS"

# Count text elements
TEXT_ELEMENTS=$(grep -c '<text' "$RESOLVED" 2>/dev/null || echo "0")
echo "Text elements lost: $TEXT_ELEMENTS"

# Count embedded images
IMAGE_ELEMENTS=$(grep -c '<image' "$RESOLVED" 2>/dev/null || echo "0")
echo "Embedded images lost: $IMAGE_ELEMENTS"
```

**Step 7e: Present options based on impact**

If there are **zero errors**:
```
No conversion issues. Proceeding with import.
```

If there are only **LOW impact** issues:
```
Only cosmetic issues (drop shadow). Proceeding with import.
```

If there are any **HIGH impact** issues:
```
HIGH-impact issues detected. These may cause visible rendering problems.

(a) Accept — the vector is close enough for the app
(b) Open in Figma → flatten effects → re-export SVG → retry
(c) Import as PNG instead (keeps all effects, loses vector scaling)
(d) Abort — do not import this file
```

**Step 7f: Validate the converted output**

```bash
DEST_FILE="Android/app/src/main/res/drawable/${FIXED_NAME}.xml"

FILE_SIZE=$(stat -f%z "$DEST_FILE" 2>/dev/null || stat -c%s "$DEST_FILE")
FILE_SIZE_KB=$((FILE_SIZE / 1024))
PATH_COUNT=$(grep -c '<path' "$DEST_FILE" 2>/dev/null || echo "0")
NAN_COUNT=$(grep -c 'NaN' "$DEST_FILE" 2>/dev/null || echo "0")
EMPTY_PATHS=$(grep -c 'pathData=""' "$DEST_FILE" 2>/dev/null || echo "0")
GRADIENT_COUNT=$(grep -c 'gradient' "$DEST_FILE" 2>/dev/null || echo "0")

# Fail-fast checks
if [ ! -s "$DEST_FILE" ]; then
  echo "FATAL: Conversion produced an empty file."
elif [ "$PATH_COUNT" -eq 0 ]; then
  echo "FATAL: Conversion produced no path data."
fi

if [ "$NAN_COUNT" -gt 0 ]; then
  echo "WARNING: $NAN_COUNT NaN values found — should not happen with Svg2Vector"
fi

# Large file warning (>100KB may cause rendering performance issues)
if [ "$FILE_SIZE" -gt 102400 ]; then
  echo "WARNING: Output is ${FILE_SIZE_KB}KB — large vector drawables may cause"
  echo "  rendering jank. Consider using PNG/WebP instead."
fi
```

For PNG/GIF/WebP/JSON — no conversion needed, copy file directly to destination.

## Step 8: PNG size check (PNG files only)

```bash
SIZE=$(stat -f%z "$RESOLVED" 2>/dev/null || stat -c%s "$RESOLVED")
if [ "$SIZE" -gt 102400 ]; then
  SIZE_KB=$(($SIZE / 1024))
  echo "Warning: $FILENAME is ${SIZE_KB}KB (over 100KB recommended limit)"
  echo ""
  echo "Options:"
  echo "(a) Continue anyway"
  echo "(b) Optimize with cwebp: cwebp -lossless $FILENAME -o ${FIXED_NAME}.webp"
  echo "(c) Abort — I'll reduce the file size first"
fi
```

## Step 9: GIF advice (GIF files only)

```
GIF detected: $FILENAME

This will be placed in res/raw/ and referenced as R.raw.$FIXED_NAME.

Coil GIF setup checklist for meApp:
✓ coil-gif dependency (check libs.versions.toml for coil-gif)
✓ AnimatedImageDecoder registered in ImageLoader (check your Hilt ImageModule)

Usage in Compose:
  AsyncImage(
      model = AppIcons.Setup.$PROPERTY_NAME,
      contentDescription = "TODO: add description",
  )
  Note: GIFs do not animate in @Preview — verify on device or emulator.

Continue? (a) Yes  (b) Abort
```

## Step 10: Duplicate check

### Same filename

```bash
DEST_FILE="$DEST_DIR/$FIXED_NAME.$DEST_EXT"
if [ -f "$DEST_FILE" ]; then
  echo "Replacing existing $(basename $DEST_FILE)"
  cp "$SOURCE_FILE" "$DEST_FILE"
  echo "Replaced."
  # Skip to Step 11 (AppIcons check)
fi
```

### Different filename, same visual content

**For XML (Vector Drawable):**

```python
# After avocado normalization, compute canonical hash of each existing XML
# Compare against the new file's hash
# If match found → report it

import hashlib, xml.etree.ElementTree as ET, re

def vd_hash(xml_file):
    NS = "http://schemas.android.com/apk/res/android"
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        vp = f"{root.get(f'{{{NS}}}viewportWidth')}x{root.get(f'{{{NS}}}viewportHeight')}"
        paths = sorted([
            (
                re.sub(r'\s+', ' ', re.sub(r',', ' ', p.get(f'{{{NS}}}pathData', ''))).strip(),
                p.get(f'{{{NS}}}fillColor', ''),
            )
            for p in root.iter(f'{{{NS}}}path')
        ])
        return hashlib.sha256(str(paths + [vp]).encode()).hexdigest()
    except Exception:
        return None

new_hash = vd_hash(converted_file)
for existing_xml in glob("Android/app/src/main/res/drawable/*.xml"):
    if vd_hash(existing_xml) == new_hash:
        print(f"Visual duplicate found: {existing_xml}")
```

**For PNG (PHASH via imagemagick):**

```bash
if [ "$SKIP_PHASH" != "true" ]; then
  for existing_png in Android/app/src/main/res/drawable/*.png; do
    score=$(compare -metric PHASH "$existing_png" "$SOURCE_FILE" null: 2>&1 || echo "999")
    # score < 1.0 means near-identical
    is_dup=$(awk "BEGIN { print ($score < 1.0) ? \"yes\" : \"no\" }" 2>/dev/null || echo "no")
    if [ "$is_dup" = "yes" ]; then
      echo "Visual duplicate found: $(basename $existing_png) (PHASH score: $score)"
    fi
  done
fi
```

**On duplicate found — prompt:**

```
ic_house.xml appears visually identical to what you're importing.

(a) Replace ic_house.xml with the new file
(b) Keep both files (they are intentionally different)
(c) Abort — don't import
```

## Step 11: Copy file to destination

```bash
# Create destination directory if needed (should already exist)
mkdir -p "$DEST_DIR"

# Copy the (converted/normalized) file
cp "$SOURCE_FILE" "$DEST_FILE"
echo "✓ Copied to $DEST_FILE"
```

## Step 12: Night mode check

```bash
NIGHT_DIR="Android/app/src/main/res/drawable-night"
NIGHT_FILE="$NIGHT_DIR/$(basename $DEST_FILE)"

if [ "$DEST_DIR" = "Android/app/src/main/res/drawable" ] && [ ! -f "$NIGHT_FILE" ]; then
  # Check if XML uses ?attr/ tint — if so, no night variant needed
  if [ "${DEST_EXT}" = "xml" ] && grep -q 'attr/' "$DEST_FILE"; then
    echo "ℹ Night mode: uses ?attr/ tint — no drawable-night/ variant needed."
  else
    echo "⚠ Warning: no drawable-night/ variant found at $NIGHT_FILE"
    echo "  If this drawable has hardcoded colors, add a night variant."
    echo "  Tip: use android:tint=\"?attr/colorOnSurface\" to avoid needing one."
  fi
fi
```

## Step 13: AppIcons.kt group selection

Convert the fixed filename to a PascalCase property name:
```
ic_arrow_back → ArrowBack
ic_close_outlined → CloseOutlined
wifi_ap_mode → WifiApMode
brand_logo → BrandLogo
```

Ask the user:

```
Which AppIcons group should AppIcons.<PropertyName> be added to?

(a) Default       — generic UI icons, brand logos
(b) Outlined      — outlined variants
(c) Filled        — filled/selected variants
(d) Selection     — selection state icons
(e) Metrics       — body metric icons
(f) Connection    — bluetooth/wifi icons
(g) Milestone     — achievement icons
(h) Integrations  — third-party logos + permission screens
(i) Setup         — scale setup assets, R.raw.* GIFs
(j) New group: [enter name]
```

**For Setup group with SKU suffix** (e.g., `_0384`, `_0375`, `_0376`):
```
This looks like a SKU-specific resource (detected suffix: _0384).
Should this be:
(a) A standalone val: val <Name> = R.raw.<filename>
(b) Add to an existing fun (I'll show you the relevant functions in Setup)
```
If (b): display the relevant `fun` from AppIcons.kt and tell the user to add it manually.
Never auto-generate `fun` entries.

## Step 14: Append to AppIcons.kt

```bash
APPICONS="Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt"
TMP_FILE="${APPICONS}.tmp"
cp "$APPICONS" "$TMP_FILE"
```

The new line to insert:
```kotlin
val $PROPERTY_NAME = R.$R_PREFIX.$FIXED_NAME
```

Find the closing `}` of the selected group and insert the val before it.
Write the modified content to `$TMP_FILE`, then `mv "$TMP_FILE" "$APPICONS"`.

Verify the insertion looks correct by showing the user the updated group:
```
Added to AppIcons.$GROUP:
  val $PROPERTY_NAME = R.$R_PREFIX.$FIXED_NAME
```

## Step 15: Build verification

```bash
cd Android && ./gradlew assembleDebug 2>&1 | tail -20
```

If build fails, diagnose and fix:

| Error message | Fix |
|---|---|
| `Unresolved reference: $PROPERTY_NAME` | Property name mismatch — fix AppIcons.kt entry |
| `None of the following candidates` | Wrong R.drawable vs R.raw prefix — correct it |
| `Expecting member declaration` | Syntax error in appended val — fix formatting |
| `Duplicate property name` | Entry already exists — remove the duplicate |
| `Resource not found` | Filename mismatch — verify the file was copied correctly |

Re-run after each fix. Only revert AppIcons.kt as last resort if error is undiagnosable.

## Step 16: Generate @PreviewTheme composable

Print to console:

```
✓ Import complete! Here's your @PreviewTheme snippet:
Paste this into a preview file in features/common/components/ or your feature directory.
```

**For Vector Drawable (XML):**
```kotlin
@PreviewTheme
@Composable
fun ${PROPERTY_NAME}Preview() {
    MeAppTheme {
        Icon(
            painter = painterResource(AppIcons.${GROUP}.${PROPERTY_NAME}),
            contentDescription = "TODO: add meaningful description for accessibility",
            modifier = Modifier.size(24.dp)
        )
    }
}
```

**For PNG or WebP:**
```kotlin
@PreviewTheme
@Composable
fun ${PROPERTY_NAME}Preview() {
    MeAppTheme {
        Image(
            painter = painterResource(AppIcons.${GROUP}.${PROPERTY_NAME}),
            contentDescription = "TODO: add meaningful description for accessibility",
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

**For GIF (AsyncImage with Coil):**
```kotlin
@PreviewTheme
@Composable
fun ${PROPERTY_NAME}Preview() {
    MeAppTheme {
        // Note: GIFs do not animate in @Preview — verify on device or emulator
        AsyncImage(
            model = AppIcons.${GROUP}.${PROPERTY_NAME},
            contentDescription = "TODO: add meaningful description for accessibility",
            modifier = Modifier.size(120.dp)
        )
    }
}
```

Then print a final summary:
```
✓ Import complete
  File:     res/$DEST_FOLDER/$FIXED_NAME.$DEST_EXT
  AppIcons: AppIcons.$GROUP.$PROPERTY_NAME = R.$R_PREFIX.$FIXED_NAME
  Build:    PASSED
```

</process>

<success_criteria>
drawable-import skill is complete when:

- [ ] File path is validated (path traversal + filename allowlist)
- [ ] Idempotency: already imported + registered → exits cleanly with message
- [ ] Filename auto-fixed to snake_case; asks when prefix is ambiguous
- [ ] SVG converted via `./gradlew :app:convertSvg` (Google's Svg2Vector, same as Android Studio)
- [ ] Conversion output validated: file size, path count, NaN check, empty path check, gradient count
- [ ] ALL conversion errors captured and reported — every ERROR line from converter is shown
- [ ] Errors grouped into logical issues (e.g. 7 filter errors = 1 "Drop Shadow" issue)
- [ ] Each issue classified by impact level: HIGH (mask, text, image, clipPath), MEDIUM (gradient, pattern), LOW (filter/shadow)
- [ ] For HIGH-impact issues: source SVG analyzed to count affected paths/elements
- [ ] Full error report printed with: raw errors, what is lost, visual impact description
- [ ] User prompted with options only for HIGH-impact issues; LOW issues auto-accepted
- [ ] PNG > 100KB → warning + options (continue/cwebp/abort)
- [ ] GIF → routed to res/raw/ with Coil setup checklist printed
- [ ] Same filename → auto-replaced silently with log message
- [ ] Visual duplicate (different name) → user prompted with 3 options
- [ ] File copied to correct res/ folder
- [ ] Night mode warning shown for drawables without ?attr/ tint
- [ ] AppIcons group selection presented with all 9 options
- [ ] SKU suffix detected → asks val vs fun; never auto-generates fun entries
- [ ] PascalCase property name generated correctly
- [ ] `val` entry appended to correct group via atomic write
- [ ] `./gradlew assembleDebug` runs and passes; errors diagnosed and fixed
- [ ] @PreviewTheme snippet printed with correct type (Icon/Image/AsyncImage)
- [ ] contentDescription = "TODO: add meaningful description" included

</success_criteria>
