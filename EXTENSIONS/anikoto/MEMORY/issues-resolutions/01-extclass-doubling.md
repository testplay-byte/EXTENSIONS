# Issue 01: extClass Doubling — ClassNotFoundException

> Date: 2026-06-23 (sessions 15-16) · Status: ✅ RESOLVED & VERIFIED
> Severity: CRITICAL (extension crashes on load — never appears in Aniyomi)
> First seen: session 13 testing · Fixed: session 15 · Verified: session 16

## Symptom

After installing the extension and trusting it in Aniyomi/Animiru, the extension **disappears**
from the list. The logcat shows:

```
ClassNotFoundException: Didn't find class
"eu.kanade.tachiyomi.animeextension.en.anikoto.en.anikoto.Anikoto"
```

Note the **DOUBLED** class name: `...anikoto.en.anikoto.Anikoto` instead of `...anikoto.Anikoto`.

## Root cause

The `extClass` manifest meta-data was set to `.en.anikoto.Anikoto` (the full relative path),
but the Aniyomi `AnimeExtensionLoader.loadExtension()` resolves the class name by prepending
the **full package name** (`pkgInfo.packageName`):

```kotlin
// AnimeExtensionLoader.kt line ~293
if (sourceClass.startsWith(".")) {
    pkgInfo.packageName + sourceClass  // prepends the FULL package name
}
```

So: `eu.kanade.tachiyomi.animeextension.en.anikoto` + `.en.anikoto.Anikoto` = `eu.kanade.tachiyomi.animeextension.en.anikoto.en.anikoto.Anikoto` — WRONG (doubled).

## Why it happened

In session 08, the `extClass` was set to `.en.anikoto.Anikoto` following the pattern
`<namespace>/<lang>/<name>/<Class>`. But this is wrong — the `applicationIdSuffix = "en.anikoto"`
already makes the full package name `eu.kanade.tachiyomi.animeextension.en.anikoto`, and the
loader prepends that to `extClass`.

## The fix

Changed `extClass` in `build.gradle.kts`:

```kotlin
// BEFORE (wrong):
val extClass = ".en.anikoto.Anikoto"

// AFTER (correct):
val extClass = ".Anikoto"  // just the class name — the loader prepends the full packageName
```

The loader now resolves to: `eu.kanade.tachiyomi.animeextension.en.anikoto` + `.Anikoto` = `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` ✓

## Verification

- Manifest: `extClass = ".Anikoto"` ✓ (via `aapt2 dump xmltree`)
- DEX: correct class `Leu/kanade/tachiyomi/animeextension/en/anikoto/Anikoto;` present (127 refs) ✓
- DEX: doubled class `Leu/.../anikoto/en/anikoto/Anikoto;` NOT present (0 refs) ✓
- Runtime: extension loads successfully after trusting (no more ClassNotFoundException) ✓

## The lesson (CHECKLIST ITEM)

**extClass must be `.ClassName`** (one dot + class name only). Never include the package path.
The loader prepends the full `packageName` automatically.

**Always verify against the reference APK's manifest** before building. The reference v3 uses:
- package: `...en.anikotofinal`
- extClass: `.AnikotoFinal` (just the class name)
- → resolves to `...en.anikotofinal.AnikotoFinal` ✓

## Related files

- `build.gradle.kts` (the fix)
- `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/01-apk-overview.md` (reference v3 manifest)
- `SHARED/REFERENCE_HUB/aniyomi-app/.../AnimeExtensionLoader.kt` (the loader code)
