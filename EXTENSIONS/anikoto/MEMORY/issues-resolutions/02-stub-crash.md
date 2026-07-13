# Issue 02: Stub! Crash — Stubs Compiled Into APK

> Date: 2026-06-23 (session 16) · Status: ✅ RESOLVED & VERIFIED
> Severity: CRITICAL (extension crashes on instantiation — `Exception("Stub!")`)
> First seen: session 16 testing · Fixed: session 16 · Verified: session 16

## Symptom

After fixing the extClass doubling (issue 01), the extension loaded but immediately crashed
with:

```
Caused by: java.lang.Exception: Stub!
    at eu.kanade.tachiyomi.animesource.online.AnimeHttpSource.<init>(AnimeHttpSource.kt:21)
    at eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto.<init>(Anikoto.kt:42)
```

The `Anikoto` constructor calls `super()` (AnimeHttpSource constructor), which throws
`Exception("Stub!")`.

## Root cause

The ext-lib v16 **stub source files** (AnimeHttpSource, Video, NetworkHelper, Hoster, etc.)
were compiled **directly INTO the extension's APK**. At runtime, Aniyomi's
`ChildFirstPathClassLoader` (child-first!) finds the stubs in the extension's DEX **before**
the app's real classes. The stub constructor runs `throw Exception("Stub!")` → crash.

This happened because the stubs were placed in `src/en/anikoto/src/main/kotlin/` alongside
the extension code. They got compiled into the main DEX and packaged in the APK.

## Why it happened

In session 08, the ext-lib v16 stubs were copied directly into the extension's source tree
to work around JitPack AAR / Kotlin metadata issues. This worked for compilation but the
stubs ended up in the APK at runtime.

The `ChildFirstPathClassLoader` is designed to load extension classes from the extension's
DEX first (child-first), then fall back to the parent (app) classloader. This is correct
for extension classes, but WRONG for stub classes — the stubs should only exist at compile
time, not runtime.

## The fix

Moved the 26 stub files into a **separate `:stubs` Gradle module** and depend on it as
`compileOnly`:

### 1. Created `stubs/` module
```
stubs/
├── build.gradle.kts          ← android.library plugin, all deps compileOnly
└── src/main/kotlin/
    └── eu/kanade/tachiyomi/
        ├── AppInfo.kt
        ├── animesource/       ← AnimeHttpSource, Video, Hoster, etc.
        ├── network/           ← NetworkHelper, Requests, OkHttpExtensions, etc.
        └── util/              ← JsoupExtensions, JsonExtensions, etc.
```

### 2. Updated `settings.gradle.kts`
```kotlin
include(":stubs")           // ← NEW
include(":src:en:anikoto")
```

### 3. Updated `build.gradle.kts` (root)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false  // ← NEW
    alias(libs.plugins.kotlin.serialization) apply false
}
```

### 4. Updated `src/en/anikoto/build.gradle.kts`
```kotlin
dependencies {
    compileOnly(project(":stubs"))  // ← NEW: compileOnly, NOT in the APK
    // ... other compileOnly deps
}
```

### 5. Removed stubs from extension src
Deleted `src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/{animesource,network,util,AppInfo.kt}`
— only `animeextension/` remains.

## Verification

- **"Stub!" string in DEX**: 0 occurrences ✓ (was present before)
- **AnimeHttpSource class DEFINITION in DEX**: 0 ✓ (not in APK)
- **Video class DEFINITION in DEX**: 0 ✓ (not in APK)
- **NetworkHelper class DEFINITION in DEX**: 0 ✓ (not in APK)
- **Anikoto class DEFINITION in DEX**: 1 ✓ (our class IS in APK)
- **APK size**: 102 KB (down from 147 KB — 45 KB of stub DEX removed)
- **Runtime**: extension loads and instantiates successfully ✓

## The lesson (CHECKLIST ITEM)

**Stubs must be `compileOnly` — NEVER in the APK's DEX.** The stubs' method bodies are
`throw Exception("Stub!")` — they exist only to satisfy the compiler. At runtime, the app's
`ChildFirstPathClassLoader` would find them in the extension's DEX first and execute the
stub bodies → crash.

**Always use a separate `:stubs` module** (or the JitPack AAR if it works) with
`compileOnly(project(":stubs"))`. Never place stub source files in the extension's
`src/main/kotlin/`.

## How to verify stubs are NOT in the APK

```bash
# Check for "Stub!" string (should be 0):
dexdump -l plain APK 2>/dev/null | grep -c 'Stub!'

# Check for stub class DEFINITIONS (should be 0):
dexdump APK 2>/dev/null | grep -c 'Class descriptor.*AnimeHttpSource;'
```

## Related files

- `stubs/build.gradle.kts` (the stubs module)
- `stubs/src/main/kotlin/` (the 26 stub files)
- `settings.gradle.kts` (includes `:stubs`)
- `build.gradle.kts` (root — declares android.library plugin)
- `src/en/anikoto/build.gradle.kts` (compileOnly(project(":stubs")))
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` (the discrepancy doc)
