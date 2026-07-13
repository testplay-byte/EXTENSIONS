# issues-resolutions/ — Resolved Issues & Lessons

> **Status: VERIFIED.** Each entry is a problem that was actually hit, actually diagnosed, and
> actually fixed — with proof. Read this to avoid repeating mistakes.

## What goes here

- Bugs, build failures, runtime errors, scraping failures that have been **resolved and verified**.
- Wrong assumptions that were corrected (the lesson, not the wrong assumption itself).
- Regressions and their root cause.

## What does NOT go here

- **Unresolved** issues → keep in `TEMPORARY_MEMORY/` until fixed and verified, then promote here.
- General research → `research/`.
- Design choices → `decisions/`.

## Promotion rule (MANDATORY)

An issue stays in `TEMPORARY_MEMORY/` while it is open. Only **after** it is fixed and verified do
you write a clean resolution entry here and **delete** the temp note. Promote by moving the
*knowledge*, not the raw scratch.

## Entry template (suggested)

```markdown
# Issue: <short title>

> Date hit: YYYY-MM-DD · Date resolved: YYYY-MM-DD · Status: Resolved & Verified

## Symptom
What went wrong, what was observed (logs, behavior).

## Root cause
The actual underlying cause (verified, not guessed).

## Fix
What change resolved it (one change at a time).

## Verification
How we proved the fix works (build OK, browser test, device test, log output).

## Prevention
How to avoid hitting this again.
```

## Naming

`YYYY-MM-DD_issue-<short-title>.md`

## Current contents

1. **`01-extclass-doubling.md`** — ClassNotFoundException: doubled class name. `extClass` was
   `.en.anikoto.Anikoto` but should be `.Anikoto` (the loader prepends the full packageName).
   Fixed in session 15.
2. **`02-stub-crash.md`** — `Exception("Stub!")`: ext-lib v16 stubs were compiled INTO the APK,
   and `ChildFirstPathClassLoader` loaded them at runtime instead of the app's real classes.
   Fixed by moving stubs to a separate `:stubs` module with `compileOnly` dependency.
   Fixed in session 16.
3. **`03-versionid-logo-bumping.md`** — Missing `versionId` meta-data, blue placeholder logo,
   and the version-bump practice established (bump `versionCode` + `versionId` on every rebuild,
   delete old APKs, record MD5). Fixed in session 15.

## Related

- `MEMORY/guides/04-build-checklist.md` — ★ the mandatory pre/post-build checklist (created
  after issues 01-03 to prevent repeating these mistakes)
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — the stub vs runtime discrepancy
  doc (the root cause of issue 02)
