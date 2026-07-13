# TEMPORARY_MEMORY — Raw, Unverified Notes

> **Status: DRAFT ZONE.** Everything here is unverified until promoted. Do not trust content here
> as fact — it is hypothesis and work-in-progress.

## What goes here

- Raw research notes before they're confirmed against source code / live site / a successful build.
- Hypotheses and "I think X works like Y" notes.
- Open issues currently under investigation (symptoms logged, root cause not yet found).
- Scratch calculations, endpoint dumps, half-finished analysis.
- Anything that is **not yet verified**.

## What does NOT go here

- Anything already verified → it belongs in a mature folder (`research/`, `guides/`, etc.).
- Session logs → `session-logs/`.
- Final decisions → `decisions/`.

## The promotion workflow (MANDATORY)

1. Write your raw note here with a clear status label (`HYPOTHESIS:`, `TODO:`, `INVESTIGATING:`).
2. When the note is **verified** (confirmed against source/live site/build):
   - Format it properly (clean structure, cite sources, `VERIFIED:` label).
   - **Move** the file to the appropriate mature folder (`research/`, `guides/`, `ext-lib/`, `sites/`).
   - **Delete** the temp file. Promote by moving, never copying.
3. If the note turns out to be **wrong**:
   - Don't promote the wrong content. Instead, write a short lesson into `issues-resolutions/`
     (what was hypothesized, why it was wrong, the correct answer) and delete the temp file.
4. If the note is an **unresolved issue**: it stays here until resolved. Once resolved AND verified,
   promote the finding + resolution to `issues-resolutions/` and delete the temp file.

## Naming

`YYYY-MM-DD_short-kebab-case-title.md`

Example: `2026-06-22_hsub-vs-sub-token-sharing-hypothesis.md`

## Current contents

_(empty — populated as research begins)_
