# AniKoto 180 — Maintenance Workflow Folder

> **Purpose:** one self-contained folder so any future session can handle AniKoto 180 issues
> **the exact same way**, from first report to published release + notification.
> Created 2026-09-13 (session 55) at the user's request.

## Reading order (for a new session)

0. **★ Fresh sandbox / new agent?** First clone the dev repo and read
   `EXTENSIONS/anikoto/AGENT_ONBOARDING.md` — that committed file is the always-available
   entry point (this folder is sandbox-local and may not exist after a reset).
1. **`WORKFLOW.md`** — the master process: restore → triage → diagnose → fix & build →
   **publish** → verify → memory + ntfy. Start here, follow the phases.
2. **`REFERENCE.md`** — hard facts you must never get wrong (IDs, URLs, versions, signing cert,
   source-site chain, repo anatomy, sandbox paths).
3. **`TROUBLESHOOTING.md`** — ready-made playbooks for every issue class seen so far
   (playback breakage chain-probe, in-app download/stale version, website, icon, CI).
4. **`TOOLS.md`** — scrapling + scrapegraphai: installed status, real API cheatsheets
   (introspected on the installed versions), Anikoto-specific use cases, activation plan.
5. **`SECRETS.md`** — PAT1/PAT2 + ntfy topic (**private, never commit**).

## Quick start (returning session, 30 seconds)

```bash
git -C /home/z/extensions-repo pull --ff-only        # dev repo (PAT1 for push)
git -C /home/z/aniyomi-extensions-180 fetch origin   # dist repo (PAT2 for push)
source /home/z/scrape-venv/bin/activate              # scrapling + scrapegraphai ready
```

Then open `WORKFLOW.md` Phase 1 and follow from there. Read the newest
`/home/z/extensions-repo/EXTENSIONS/anikoto/MEMORY/session-logs/` entries first — they carry
the latest ground truth.

## Non-negotiables (short list; full version in WORKFLOW.md ⭐)

- Source ID `178825880993122333` / package name / signing key: **immutable**.
- Old APKs are **never deleted** on publish.
- Distribution repo (`Confused-Creature-180/aniyomi-extensions`): **surgical commits only**.
- Every session ends with: session log + worklog append + `ntfy.sh/THE-TASK-IS-DONE`.

## Folder contents

| File | Contents |
|---|---|
| `WORKFLOW.md` | Master end-to-end process incl. publishing |
| `REFERENCE.md` | Identifiers, URLs, versions, chain, paths |
| `TROUBLESHOOTING.md` | Issue playbooks + current open items |
| `TOOLS.md` | scrapegraphai & scrapling research + venv guide |
| `SECRETS.md` | Tokens (private) |
