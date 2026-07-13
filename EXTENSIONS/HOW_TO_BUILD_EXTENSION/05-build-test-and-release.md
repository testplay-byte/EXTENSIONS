# Step 5 — Build, Test, and Release

> **Finalize the extension for publishing.** Scaffold the full Gradle project (if you've been
> testing with a minimal one), sign the release APK, verify R8 doesn't break serialization, run
> the build checklist, and register the extension.
>
> **Prerequisite:** Step 4 complete (all servers play).
> **Done when:** a signed release APK passes the build checklist; the extension is registered in
> `MEMORY/EXTENSIONS.md` with status ✅.

---

## 5.1 Scaffold the full Gradle project (if not already)

If you've been testing with a minimal project, ensure it has the full structure. The fastest path:
copy AniKoto's `DEV/` build system and adapt.

- [ ] `EXTENSIONS/<name>/DEV/settings.gradle.kts` — includes `:stubs` + `:src:<lang>:<name>`
- [ ] `EXTENSIONS/<name>/DEV/build.gradle.kts` — root, minimal (plugin aliases, apply false)
- [ ] `EXTENSIONS/<name>/DEV/gradle/libs.versions.toml` — `aniyomi-lib = com.github.aniyomiorg:extensions-lib:v16`
- [ ] `EXTENSIONS/<name>/DEV/gradle/kei.versions.toml` — plugin IDs + SDK versions (`java = "17"`)
- [ ] `EXTENSIONS/<name>/DEV/gradle/build-logic/` — convention plugins (adapt from AniKoto/yuzono)
- [ ] `EXTENSIONS/<name>/DEV/common/AndroidManifest.xml` + `proguard-rules.pro`
- [ ] `EXTENSIONS/<name>/DEV/stubs/` — ext-lib v16 stubs (`compileOnly`, NOT in APK)
- [ ] `EXTENSIONS/<name>/DEV/src/<lang>/<name>/build.gradle.kts` — module build config + signing config
- [ ] `EXTENSIONS/<name>/DEV/gradlew` — wrapper (copy from AniKoto, `chmod +x`)

> **AniKoto reference (copy the build system):** `../anikoto/DEV/` — all the above files. Adapt the `applicationId`, `extClass`, `extVersionCode`, `extVersionId`, and keystore name.
> **Full build-setup guide:** `../../MEMORY/guides/01-build-setup-for-ext-lib-16.md`.

---

## 5.1b Prepare the app icon (★ IMPORTANT — crop transparent borders)

The user provides a high-resolution icon (typically 1000×1000 or 1024×1024 PNG with transparency).
The icon often has large transparent borders around the actual artwork. **You MUST auto-crop these
transparent borders before resizing to mipmap densities**, otherwise the icon appears small and
poorly centered in the app.

### Step-by-step icon preparation

```python
from PIL import Image
import os

# 1. Open the source icon
img = Image.open('source_icon.png').convert('RGBA')

# 2. Auto-crop transparent borders (getbbox finds the non-transparent area)
bbox = img.getbbox()
if bbox:
    cropped = img.crop(bbox)
else:
    raise Exception("Image is fully transparent!")

# 3. If not square, pad to square (centered on transparent background)
w, h = cropped.size
if w != h:
    max_dim = max(w, h)
    padded = Image.new('RGBA', (max_dim, max_dim), (0, 0, 0, 0))
    padded.paste(cropped, ((max_dim - w) // 2, (max_dim - h) // 2))
    cropped = padded

# 4. Resize to 1024×1024 (full resolution for the webpage)
full = cropped.resize((1024, 1024), Image.LANCZOS)
full.save('public/<name>-icon.png', 'PNG')

# 5. Resize to all 5 Android mipmap densities
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}
for folder, size in sizes.items():
    resized = full.resize((size, size), Image.LANCZOS)
    path = f'DEV/src/<lang>/<name>/res/{folder}/ic_launcher.png'
    resized.save(path, 'PNG')
```

### Where the icon files go

| Path | Size | Purpose |
|---|---|---|
| `DEV/src/<lang>/<name>/res/mipmap-mdpi/ic_launcher.png` | 48×48 | Android ldpi |
| `DEV/src/<lang>/<name>/res/mipmap-hdpi/ic_launcher.png` | 72×72 | Android mdpi |
| `DEV/src/<lang>/<name>/res/mipmap-xhdpi/ic_launcher.png` | 96×96 | Android hdpi |
| `DEV/src/<lang>/<name>/res/mipmap-xxhdpi/ic_launcher.png` | 144×144 | Android xhdpi |
| `DEV/src/<lang>/<name>/res/mipmap-xxxhdpi/ic_launcher.png` | 192×192 | Android xxhdpi |
| `public/<name>-icon.png` | 1024×1024 | Webpage download card |

### ★ Why cropping matters

If you skip the auto-crop step:
- The icon appears small in the app (the actual artwork fills only ~75% of the square)
- The transparent borders waste space
- The icon looks unprofessional

The `getbbox()` method finds the bounding box of all non-transparent pixels. Cropping to this box
removes all transparent borders, then resizing to 1024×1024 fills the entire square.

### Temporary icon (before the user provides the real one)

If the user hasn't provided an icon yet, generate a temporary one using the image-generation skill:

```bash
z-ai image -p "Minimalist app icon logo for an anime streaming app called <Name>, flat design, rounded square, <color> gradient background with a white play button, clean modern vector style, no text" -o "DEV/src/<lang>/<name>/res/mipmap-xxxhdpi/ic_launcher.png" -s 1024x1024
```

Then copy to all densities + the webpage. Replace with the user's real icon when provided (re-run
the crop + resize process above).

---

## 5.2 Configure the module build.gradle.kts

In `src/<lang>/<name>/build.gradle.kts`:

- [ ] `applicationId = "eu.kanade.tachiyomi.animeextension.<lang>.<name>"` — the package
- [ ] `extClass` — the FULL path to your source class. Use FULL path (no leading dot) if
      `applicationId ≠ source package`. (AniKoto: `...en.anikoto.Anikoto` because applicationId is
      `...anikoto180` but source is `...anikoto`.) See [`common-pitfalls.md`](common-pitfalls.md) §extClass.
- [ ] `extVersionCode = <N>` — bump per build
- [ ] `extVersionId = <N>` — ★ STABLE once published. Start at 1. NEVER bump after publish.
- [ ] `isNsfw = false` (or true, per Step 1 §1.8)
- [ ] **Signing config** — release builds use a keystore:
  ```kotlin
  signingConfigs {
      create("release") {
          storeFile = rootProject.file("<name>-release.jks")
          storePassword = "<password>"
          keyAlias = "<alias>"
          keyPassword = "<password>"
      }
  }
  buildTypes {
      getByName("release") {
          signingConfig = signingConfigs.getByName("release")
          isMinifyEnabled = true  // R8
          ...
      }
  }
  ```

> **AniKoto reference:** `../anikoto/DEV/src/en/anikoto/build.gradle.kts` — the full module build config.

---

## 5.3 Generate a per-extension keystore

**Do NOT reuse AniKoto's keystore.** Each extension gets its own signing key.

- [ ] Generate a new keystore:
  ```bash
  keytool -genkeypair -v \
    -keystore EXTENSIONS/<name>/DEV/<name>-release.jks \
    -alias <name> \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass <password> -keypass <password> \
    -dname "CN=<Name>, O=Confused_Creature_180, C=US"
  ```
- [ ] Record the keystore info in `EXTENSIONS/<name>/DEV/keystore-info.txt` (alias, password,
      SHA-256 fingerprint).
- [ ] Add `<name>-release.jks` to `.gitignore` (NEVER commit a keystore).
- [ ] **Keep the keystore safe** — if lost, you can't publish updates (the signature must match).

> **Pitfall:** losing the keystore = no updates. See [`common-pitfalls.md`](common-pitfalls.md) §keystore.

---

## 5.4 Configure ProGuard / R8 (★ critical — prevents serialization crash)

R8 minifies release builds. If it strips `$$serializer` classes, your DTOs crash at runtime
("type reference not found"). AniKoto hit this in session 47.

In `common/proguard-rules.pro`:

- [ ] Keep ALL your extension's classes:
  ```
  -keep class eu.kanade.tachiyomi.animeextension.<lang>.<name>.** { *; }
  ```
- [ ] Keep all `$$serializer` classes (kotlinx.serialization):
  ```
  -keepclassmembers class **$$serializer { *; }
  -keepclasseswithmembers class * {
      kotlinx.serialization.KSerializer serializer(...);
  }
  ```
- [ ] Build a release APK + verify `$$serializer` classes are in the DEX:
  ```bash
  # After assembleRelease:
  unzip -p src/<lang>/<name>/build/outputs/apk/release/*.apk classes.dex | strings | grep -c '\$\$serializer'
  # Should be > 0 (AniKoto has 23 refs)
  ```

> **AniKoto reference (★ critical):** `../anikoto/DEV/common/proguard-rules.pro` + `../anikoto/MEMORY/session-logs/2026-06-26_session-47_*.md` (the R8 serialization fix).
> **Pitfall:** R8 serialization crash — see [`common-pitfalls.md`](common-pitfalls.md) §r8-serialization.

---

## 5.5 Build + verify (the mandatory checklist)

Follow [`../../MEMORY/guides/04-build-checklist.md`](../../MEMORY/guides/04-build-checklist.md)
BEFORE and AFTER every build. Key checks:

- [ ] `extClass` = FULL path (no leading dot) if applicationId ≠ source package
- [ ] `applicationId` = `eu.kanade.tachiyomi.animeextension.<lang>.<name>`
- [ ] "Stub!" count = 0 in the release DEX (stubs must NOT be in the APK)
- [ ] `versionCode` bumped from last build; `versionId` STABLE
- [ ] `$$serializer` classes present in release DEX (R8 didn't strip them)
- [ ] Signing verified (v1 + v2 signatures; SHA-256 matches `keystore-info.txt`)
- [ ] Settings categories render correctly
- [ ] No crashes on install + first load

```bash
source /home/z/my-project/.android-env.sh
cd /home/z/my-project/EXTENSIONS/<name>/DEV

./gradlew :src:<lang>:<name>:assembleDebug --no-daemon    # testing build
./gradlew :src:<lang>:<name>:assembleRelease --no-daemon  # release build (signed, R8)
```

> **Build checklist (★ mandatory):** `../../MEMORY/guides/04-build-checklist.md`.

---

## 5.6 Test end-to-end on a device/emulator

- [ ] Install the release APK in Aniyomi/Animiru (+ a fork or two for compat testing)
- [ ] Browse Popular → loads, covers render
- [ ] Browse Latest → loads
- [ ] Search "one piece" → results
- [ ] Apply a filter (genre + sort) → filtered results
- [ ] Tap an anime → details render
- [ ] Episode list loads, ascending, sub/dub via scanlator
- [ ] Tap an episode → server picker
- [ ] **Test EVERY server** → each plays a video (rule §1)
- [ ] Change a setting → behavior changes
- [ ] Saved anime survive an app restart (versionId stable)
- [ ] Check logcat for errors (`adb logcat -s <Tag>:*`)

---

## 5.7 Copy APKs + register

- [ ] Copy the release + debug APKs to `EXTENSIONS/<name>/APK/`:
  ```bash
  cp src/<lang>/<name>/build/outputs/apk/release/*.apk ../APK/
  cp src/<lang>/<name>/build/outputs/apk/debug/*.apk ../APK/
  ```
- [ ] Update `EXTENSIONS/<name>/EXTENSION.md` — status → ✅ All features working; fill in the "Current status" section.
- [ ] Update `EXTENSIONS/<name>/APK_INFO.md` — full APK info (size, SHA-256, versionCode, etc.)
- [ ] **Register in `MEMORY/EXTENSIONS.md`** — update the status row to ✅.
- [ ] Write a session log in `MEMORY/session-logs/` documenting the release.

---

## 5.8 Post-release: consider enhancements

Once the v1 release is stable, consider these (all optional, all in AniKoto):

- **Episode metadata enrichment** — thumbnails + titles + descriptions from Jikan/AniList/Kitsu. Reference: `../anikoto/MEMORY/modules/04-episode-metadata.md`.
- **Smart Search** — AI-powered search via Google AI Search. Reference: `../anikoto/MEMORY/modules/06-smart-search.md`.
- **Performance optimizations** — WebView pre-warming, parallel fetching. Reference: `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`.
- **Promo line** in descriptions (if you want to credit yourself). Reference: AniKoto appends "Thank the Confused_creature_180".

Each enhancement = one session, one change at a time (rule §2), verified before the next.

---

## 5.9 Verification checklist (Step 5 is done when ALL pass)

- [ ] Full Gradle project scaffolded (settings, build-logic, stubs, common, gradle wrapper)
- [ ] Module build.gradle.kts configured (applicationId, extClass, versionCode, versionId, signing, R8)
- [ ] Per-extension keystore generated + in `.gitignore` + info recorded
- [ ] ProGuard rules keep all extension classes + `$$serializer` classes
- [ ] Release APK builds + passes the build checklist (`MEMORY/guides/04-build-checklist.md`)
- [ ] End-to-end test on device: all 5 steps work (catalog → details → episodes → playback → settings)
- [ ] ALL servers play (rule §1)
- [ ] Saved anime survive restart (versionId stable)
- [ ] APKs copied to `EXTENSIONS/<name>/APK/`
- [ ] `EXTENSION.md` + `APK_INFO.md` updated
- [ ] Registered in `MEMORY/EXTENSIONS.md` (status ✅)
- [ ] Session log written

**When all pass → the extension is released.** 🎉

---

## What to ask the user about (common Step 5 questions)

- "What should the keystore password / alias be?" (Or: "I'll use the same convention as AniKoto — `<name>` / `<password>` — confirm?")
- "versionId = 1 for the first release. Confirm we'll never bump this?"
- "The release APK is 268KB. Ready to publish, or any final changes?"
- "Should I add a promo line to descriptions (like AniKoto's 'Thank the Confused_creature_180')?"
