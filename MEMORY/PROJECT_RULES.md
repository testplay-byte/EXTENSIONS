# PROJECT_RULES — Core Principles (Non-Negotiable)

> These rules govern the entire project. Read at the start of every session.
> The most important ones: **verify before trusting**, **one change at a time**,
> **don't force anything**, **document everything**. When in doubt, ask the user.

---

## 1. Research & Verification

- **Don't copy the reference APK.** Build from your own understanding and research. Even if there
  are errors, they're learning lessons. The APK is a cross-check only, not a copy source.
- **Don't blindly trust — verify everything.** Don't blindly believe what the user tells you either.
  Check yourself, verify against the live site, and only then trust. If the user says something
  exists, go confirm it.
- **Don't make changes based on the user's findings directly.** When the user reports an issue,
  check it yourself first. Only make a change if you verify it's actually needed. Don't force anything.
- **Test ALL servers from ALL endpoints.** Don't conclude from one API response. A site has multiple
  server-list paths — test each one's full resolve chain.
- **Use a real browser (agent-browser) to verify.** Curl API responses alone can miss things. Load
  the page and capture network requests to see what actually happens.
- **You can OCR the webpage** to check available servers and options if needed.

## 2. Development Approach

- **One change at a time.** Approach improvements one at a time with proper verification before each
  build. No bundled multi-fix changes (that caused regressions).
- **Don't force anything.** If you think you can't handle something properly, tell the user. Don't
  mess up the workspace, the environment, or the code.
- **Be honest about what you can and can't do.** Don't claim success when you haven't verified.
  Report honestly.
- **Handle everything properly, smoothly, and with proper analysis and understanding.** Don't rush.
  Think through each change.

## 3. Documentation

- **Make documentation properly formatted and easy to navigate.** It should be useful for future
  sessions.
- **Update documentation as needed** whenever you make changes.
- **Document everything along the way** — research findings, guides, decisions, issues, resolutions.
- **The workflow is modifiable.** If you discover a gap at a later stage that should have been
  handled earlier, add it to the workflow and log the revision.

## 4. Memory System

- **Two-tier memory:** `TEMPORARY_MEMORY` (raw, unverified) → mature folders (verified). Nothing goes
  directly to mature folders until verified.
- **Promote by moving, not copying.** Delete the temp file when promoting.
- **Session logs are mandatory.** At the end of every working session, write a session log so the
  next session can pick up with zero context loss.

## 5. Workspace Management

- **`SHARED/REFERENCE_HUB/` is read-only.** Never modify files in the reference repos.
- **`EXTENSIONS/` is for active development.** Each extension gets its own self-contained folder
  (`EXTENSIONS/<name>/`) holding its Gradle project, keystore, APKs, and knowledge base.
- **Each extension is copied from `EXTENSIONS/_template/`** to start. See `MEMORY/guides/02-how-to-create-a-new-extension.md`.
- **An issue in one extension stays in that extension's folder** — diagnose, fix, and log it there
  without touching others.
- **Don't mess up the environment.** If something isn't working, revert rather than break things
  further.

## 6. Logging (logcat-only — updated session 46)

- **All logging goes to Android logcat** (tag "Anikoto"). Capture with `adb logcat -s Anikoto:*`.
- **No file logging.** Previous versions wrote to `Download/1118000/` — this was removed in
  v16.6 (session 46) because it required `WRITE_EXTERNAL_STORAGE` and cluttered the user's
  device. The extension is mature enough that logcat is sufficient for debugging.
- **The `AnikotoLog` object** provides `i()`, `d()`, `w()`, `e(msg, throwable?)`, `trunc()`
  methods — all delegate to `android.util.Log`. No file I/O, no permissions needed.
- **Make logs detailed enough to pinpoint issues** — not too noisy, not too silent.
- **Use `AnikotoLog.trunc(s, maxLen)` for long strings** (URLs, tokens, response bodies) to
  avoid logcat's 4KB line limit.

## 7. Audio Types & Labeling

- **The site has 3 audio types:** sub (SUB), hsub (HSUB/hardsub), dub (DUB) — not 2.
- **Labels should match the site's own terminology.**
- **Don't show "H-Sub" for what is actually "Sub"** — verify which data-type serves which audio
  content.
- **The site shares tokens across audio types** — same video served under different labels. Handle
  deduplication.

## 8. Episode Display

- **Sub/dub availability should be shown properly** — not crammed into the episode name on the right
  side. Use the **scanlator field** (like the reference APK does) so it shows below the episode name.
- **Episode names should stay clean** — just the episode number and title, no brackets or tags.

## 9. File Management

- **Each extension's APKs live in `EXTENSIONS/<name>/APK/`** (debug + release copies) — easy to find,
  co-located with that extension's Gradle project.
- **The download webpage** (`src/` + `public/`) serves the current extension's APK via
  `/api/apk?type=release|debug` — the API reads from the extension's Gradle build output.
- **Don't commit built APKs to the extension's source tree** — they're rebuildable. The `APK/`
  folder and `DEV/build/` are the only APK locations; both are gitignored.
- **The user will test the APK** — you build, the user tests, the user reports back.

---

## Summary

The most important principles: **verify before trusting**, **one change at a time**,
**don't force anything**, and **document everything**. When in doubt, ask the user rather than
guessing.
