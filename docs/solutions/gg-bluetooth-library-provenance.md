# ggBluetoothNativeLibrary — Dependency Provenance Assessment

**Date**: 2026-03-24
**Ticket**: MA-3367

## Summary

`gg-bluetooth-android` is a first-party Bluetooth Low Energy (BLE) library maintained
by DMD Brands. It is NOT a third-party open-source dependency.

## Identification

| Field                | Value                                                          |
|----------------------|----------------------------------------------------------------|
| Maven coordinates    | `com.dmdbrands.lib:gg-bluetooth-android:1.5.9`                |
| Source repository    | `github.com/dmdbrands/ggBluetoothNativeLibrary`               |
| Registry             | GitHub Packages (`maven.pkg.github.com/dmdbrands/ggBluetoothNativeLibrary`) |
| Organization         | DMD Brands (`dmdbrands` GitHub org)                            |
| License              | Proprietary (internal)                                         |
| Platforms            | Android, iOS                                                   |

## Provenance Evidence

1. **Source access**: The source repository is hosted under the `dmdbrands` GitHub
   organization, the same org that owns the meApp repository.
2. **Publishing pipeline**: Published to GitHub Packages under the `dmdbrands` scope,
   requiring authenticated access via `GITHUB_USERNAME` and `GITHUB_TOKEN`.
3. **Consumer configuration**: `settings.gradle.kts` explicitly configures a Maven
   repository pointing to `maven.pkg.github.com/dmdbrands/ggBluetoothNativeLibrary`
   with organization credentials.
4. **In-repo reference**: The `bleWrapper/build.gradle.kts` module uses
   `api(libs.gg.bluetooth.android)`, and a commented-out local project reference
   (`// implementation(project(":ggBluetoothLibrary"))`) confirms it was previously
   built as a local module.

## OWASP Dependency-Check Treatment

Because this is an internal library with no NVD entries, any CVE matches produced by
OWASP dependency-check are false positives from CPE (Common Platform Enumeration) name
collisions. The library is suppressed in `Android/config/owasp-suppressions.xml` with
documentation linking back to this provenance assessment.

## Also Applies To: Vico Libraries

The same provenance assessment applies to the three Vico chart libraries:

| Maven coordinates                        | Source repository              | Version |
|------------------------------------------|--------------------------------|---------|
| `com.dmdbrands.lib:vico-core`            | `github.com/dmdbrands/vico`   | 3.0.3   |
| `com.dmdbrands.lib:vico-compose`         | `github.com/dmdbrands/vico`   | 3.0.3   |
| `com.dmdbrands.lib:vico-compose-m3`      | `github.com/dmdbrands/vico`   | 3.0.3   |

These are also published via GitHub Packages under the `dmdbrands` scope and are
suppressed identically in the OWASP suppression file.

## Risk Assessment

| Risk                        | Level  | Mitigation                                    |
|-----------------------------|--------|-----------------------------------------------|
| Supply chain compromise     | Low    | Published from org-controlled GitHub Packages  |
| Outdated transitive deps    | Medium | Review transitive deps in source repo          |
| No public vulnerability DB  | N/A    | Internal code; not indexed by NVD              |

## Recommendation

No action required. The library is maintained internally and its provenance is verified.
Continue to suppress in OWASP scans. Review transitive dependencies when updating
versions.
