# Multi-Device / Product Type Switching — User Flows

This document describes the user-facing flows for the multi-device product type switching feature. It is platform-agnostic and focuses on behaviour, not implementation.

---

## 1. Overview

The app supports multiple product types:
- **My Weight** — a personal weight scale
- **My Blood Pressure** — a blood pressure monitor (BPM)
- **Baby** — a baby scale, with one entry per registered baby (e.g. "Emma", "Liam")

A user may own one or several of these device types. The app adjusts what is shown across Dashboard, History, and Manual Entry based on the currently selected product type.

---

## 2. Signup — Choosing a Product Type

When a new user signs up, they are asked to select the product type they own before reaching the main dashboard.

**Flow:**

1. After completing account creation, the user sees a **Product Selection screen**.
2. Available options:
   - My Weight (scale)
   - My Blood Pressure (BPM)
   - Baby Scale
3. The user taps their product type and proceeds.
4. The app loads the dashboard tailored to that product type:
   - The header shows the selected product as the active item.
   - Device setup guidance (pairing screens, tutorials) matches the selected type.
   - Data shown (charts, history, metrics) is relevant to that product type.

**If the user skips or has no device yet**, the app defaults to My Weight.

---

## 3. Header Dropdown — Product Type Selector

A dropdown selector appears in the header of the **Dashboard**, **History**, and **Manual Entry** screens. It shows the currently active product and allows the user to switch.

### 3.1 What appears in the dropdown

The dropdown is populated based on what the user has set up:

| Condition | Items shown in dropdown |
|---|---|
| User has a weight scale | "My Weight" |
| User has a BPM device | "My Blood Pressure" |
| User has a baby scale + 1 registered baby | One baby row: e.g. "Emma" |
| User has a baby scale + 2 registered babies | Two baby rows: e.g. "Emma", "Liam" |
| User has a baby scale + no babies registered | No baby rows (baby option hidden until a baby is added) |
| User has multiple device types | All matching items appear together |

Each baby appears as its own separate row using the baby's name — not a generic "Baby Scale" label.

### 3.2 Ordering

Items are listed in this order:
1. My Weight (if a weight scale exists)
2. My Blood Pressure (if a BPM exists)
3. Individual baby names (one row per baby, alphabetical or by creation order)

### 3.3 Switching

When the user taps an item in the dropdown:
- The header updates to show the selected item.
- The Dashboard refreshes to show data for that product type.
- History updates to show entries for that product type (or that specific baby).
- Manual Entry updates its form fields for that product type.
- The selection is remembered. Returning to any of these screens shows the last-selected item.

---

## 4. Last-Added Device Becomes the Default Selection

Whenever the user adds a new device or registers a new baby, the newly added item automatically becomes the active selection.

**Examples:**
- User previously had "My Weight" selected. They go to Settings and add a BPM device. After setup, "My Blood Pressure" becomes the active selection.
- User adds a baby scale and registers "Emma". After setup, "Emma" becomes the active selection.
- User already has "Emma" and adds a second baby "Liam". After registering Liam, "Liam" becomes the active selection.

This means the user immediately sees data relevant to what they just set up, without having to manually switch.

---

## 5. Baby Scale — Weight Assignment Flow

When a weight measurement arrives from a baby scale via Bluetooth, the app needs to know which baby to assign it to. The logic depends on how many babies are registered and what is currently selected.

### Case 1 — Only one baby registered

The weight is automatically assigned to that baby. No prompt is shown.

### Case 2 — Two or more babies, and a baby is currently selected

If "Emma" is the active selection in the header, the incoming weight is automatically assigned to Emma. No prompt is shown.

### Case 3 — Two or more babies, and a non-baby item is currently selected

Example: the user has "My Weight" selected in the header, but a baby scale sends a reading.

In this case, an **alert appears** asking: *"Which baby is this measurement for?"*

The alert lists all registered babies by name. The user taps one baby, and the weight is assigned to that baby. The baby also becomes the active selection after assignment.

---

## 6. Settings — Device Management

The Settings screen allows users to add, rename, and remove devices and babies. Changes here are immediately reflected in the header dropdown.

### 6.1 Adding a new device

1. User opens Settings → Devices.
2. User taps "Add Device" and follows the pairing flow for their product type.
3. After successful pairing, the new device appears as an option in the header dropdown.
4. The new device becomes the active selection (see Section 4).

### 6.2 Adding a new baby

1. User opens Settings → Devices (or a dedicated Babies section).
2. User taps "Add Baby" and enters the baby's name (and optionally other details).
3. The new baby appears as its own row in the header dropdown.
4. The new baby becomes the active selection.

### 6.3 Removing a device or baby

1. User removes a device or deletes a baby profile from Settings.
2. That item is immediately removed from the header dropdown.
3. If the removed item was the active selection, the app falls back to the first remaining item in the dropdown (defaulting to My Weight if available).

### 6.4 Renaming a baby

1. User edits a baby's name in Settings.
2. The updated name appears in the header dropdown immediately.

---

## 7. Dashboard, History, and Entry — Behaviour by Product Type

| Screen | My Weight | My Blood Pressure | Baby (e.g. "Emma") |
|---|---|---|---|
| Dashboard | Weight trend, body metrics | Blood pressure readings | Baby weight trend |
| History | Weight entries | BP entries | Emma's weight entries only |
| Manual Entry | Weight + body metrics form | BP form (systolic, diastolic, pulse) | Baby weight entry form |

Switching the active product type in the header causes all three screens to update consistently.

---

## 8. Edge Cases

| Situation | Behaviour |
|---|---|
| No devices set up yet | Dropdown is hidden or shows a prompt to add a device |
| Baby scale exists but no babies registered | Baby rows are hidden; user is prompted to add a baby |
| Only one product type exists | Dropdown may be hidden or shown as a non-interactive label |
| User is on History and switches to a baby | History filters to show only that baby's entries |
| New baby is added mid-session | Baby appears in dropdown immediately without restarting the app |
