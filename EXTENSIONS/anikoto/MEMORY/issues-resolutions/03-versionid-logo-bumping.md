# Issue 03: Missing versionId Meta-data + Missing Logo + Version Bump Practice

> Date: 2026-06-23 (sessions 15-16) · Status: ✅ RESOLVED & VERIFIED
> Severity: MINOR (doesn't crash, but causes confusion + bad UX)

## Issue 3a: Missing `tachiyomi.animeextension.versionId` meta-data

### Symptom
The reference v3 APK has a `versionId` meta-data entry in its manifest; ours didn't.

### Fix
Added to `common/AndroidManifest.xml`:
```xml
<meta-data android:name="tachiyomi.animeextension.versionId" android:value="${versionId}" />
```
And in `build.gradle.kts`:
```kotlin
manifestPlaceholders["versionId"] = extVersionId.toString()
```

### Verification
Manifest now shows `tachiyomi.animeextension.versionId = 3` ✓

---

## Issue 3b: Missing/placeholder logo

### Symptom
The extension icon was a solid blue placeholder PNG (generated in session 08 as a temporary
placeholder). It looked unprofessional and was hard to distinguish from other blue icons.

### Fix
Generated proper launcher icons using PIL (Python Imaging Library):
- **Design**: teal background (#0D9488) + white play triangle (centered)
- **5 densities**: mdpi 48×48, hdpi 72×72, xhdpi 96×96, xxhdpi 144×144, xxxhdpi 192×192
- **Location**: `src/en/anikoto/res/mipmap-*/ic_launcher.png`

### Verification
`aapt2 dump resources` shows all 5 density PNGs present ✓

---

## Issue 3c: Version bump practice

### Problem
Early builds (sessions 08-14) kept `versionCode = 1` and `versionName = "16.1"` across
multiple iterations. This made it impossible to tell which build was installed on the device.

### Fix (established practice)
**Bump the version on EVERY rebuild that changes behavior:**

| Session | versionCode | versionName | APK filename | What changed |
|---------|-------------|-------------|--------------|-------------|
| 08-14 | 1 | 16.1 | `aniyomi-en.anikoto-v16.1-debug.apk` | Initial catalog + video layer |
| 15 | 2 | 16.2 | `aniyomi-en.anikoto-v16.2-debug.apk` | extClass fix, versionId meta-data, logo |
| 16 | 3 | 16.3 | `aniyomi-en.anikoto-v16.3-debug.apk` | Stubs module fix (Stub! crash) |

### Rules (CHECKLIST ITEMS)
1. **Always bump `extVersionCode`** in `build.gradle.kts` before a new build
2. **`versionName` = `16.$extVersionCode`** (ext-lib 16 requires the "16." prefix)
3. **`extVersionId`** must match `override val versionId` in `Anikoto.kt`
4. **Delete old APKs** from `EXTENSIONS/anikoto/APK/` after copying
   the new one — avoid confusion
5. **Record the MD5** so the user can verify they have the correct file

### How to bump
In `build.gradle.kts`:
```kotlin
val extVersionCode = 4  // ← bump this
val extVersionId = 4    // ← and this
```
In `Anikoto.kt`:
```kotlin
override val versionId = 4  // ← and this
```

## Related files
- `common/AndroidManifest.xml` (versionId meta-data)
- `build.gradle.kts` (version config)
- `src/en/anikoto/res/mipmap-*/ic_launcher.png` (icons)
- `Anikoto.kt` (versionId override)
