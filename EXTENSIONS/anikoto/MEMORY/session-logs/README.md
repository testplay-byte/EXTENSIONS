# session-logs/ — Mandatory End-of-Session Records

> **Status: MANDATORY.** One log per working session. This is how the next session picks up with
> zero context loss. **Always read the most recent 1–2 logs before starting a new session.**

## Rule

At the end of **every** working session, write a session log here. No exceptions. If a session
produced no progress, log that too (and why).

## Naming

`YYYY-MM-DD_session-NN_short-title.md`

- `NN` = sequential session number for that day (01, 02, …) or overall project counter — keep it
  monotonic and increasing across the project.
- `short-title` = 2–4 words on what the session focused on.

Example: `2026-06-22_session-01_initial-setup.md`

## Template

```markdown
# Session NN — <short title>

> Date: YYYY-MM-DD · Session #: NN · Duration: ~Xh

## Goal
What this session set out to do.

## What was done
- Concrete steps taken (bullet list).

## Key findings / decisions
- Anything learned or decided, with links to memory files created/updated.

## Files created / modified
- `path/to/file` — what it is.

## Status at end of session
- What works, what doesn't, what's verified vs. unverified.

## Next steps (for the next session)
- Ordered, actionable list of what to do next.
- Any open questions / blockers.

## Open issues (still in TEMPORARY_MEMORY)
- Links to unresolved temp notes.
```

## Current contents

- `2026-06-22_session-01_initial-setup.md` — project scaffolding (this session)
