# Per-Extension Decision Log Template

> **Copy this template into your extension's `MEMORY/decisions/` folder (create it) to record
> architecture decisions.** One file per decision (ADR-style). This is how future sessions
> understand WHY the extension is built a certain way.
>
> See AniKoto's decisions for examples: `../anikoto/MEMORY/` doesn't have a decisions/ subfolder
> (decisions are in the project-level `MEMORY/decisions/`), but the FORMAT is the same. For
> extension-SPECIFIC decisions, put them in `EXTENSIONS/<name>/MEMORY/decisions/`.

---

## File naming

```
EXTENSIONS/<name>/MEMORY/decisions/
├── README.md                              ← index of decisions (this section)
├── 01-<short-title>.md                    ← first decision
├── 02-<short-title>.md                    ← second decision
└── ...
```

Number decisions sequentially. Use a short kebab-case title.

---

## README.md for the decisions/ folder

```markdown
# <Extension> — Architecture Decisions

> One ADR per significant decision. Read to understand WHY the extension is built this way.

## Decisions
1. **`01-<title>.md`** — <one-line summary>
2. **`02-<title>.md`** — <one-line summary>
```

---

## ADR template (copy per decision)

```markdown
# ADR-<N>: <Decision Title>

> Date: YYYY-MM-DD · Status: Accepted (or Proposed / Superseded by ADR-<M>)

## Context
<Why is this decision needed? What problem are we solving? What constraints exist?>
<Reference the site analysis or session log that surfaced the need.>

## Decision
<What was decided? Be specific — include the chosen approach, key parameters, and any code structure.>

### The chosen approach
<Details: code structure, URL patterns, data flow, etc.>

## Rationale
<Why this approach over the alternatives? What makes it the right fit for THIS site/extension?>

## Consequences
- **Positive:** <what we gain>
- **Negative:** <what we trade off / risk>
- **Mitigations:** <how we handle the negatives>

## Alternatives considered
1. **<Alternative A>** — Rejected because <reason>.
2. **<Alternative B>** — Rejected because <reason>.

## References
- Site analysis: `../sites/<relevant-file>.md`
- Session log: `../session-logs/<session-file>.md`
- Related project-level ADR: `../../../../MEMORY/decisions/<N>-<title>.md`
- AniKoto's approach to the same problem: `../../HOW_TO_BUILD_EXTENSION/reference-prior-solutions.md` §<section>
```

---

## When to write an ADR

Write an ADR when you make a decision that:

- **Affects the extension's identity** (name, package, extClass, versionId) — these are hard to change later.
- **Affects compatibility** (fork-compat encoding, signing, versionId stability).
- **Chooses between alternatives** (e.g. "use the filter page, not the autosuggest endpoint, because X").
- **Is non-obvious** (a future session reading the code would ask "why did they do it this way?").
- **Revises a prior decision** (mark the old ADR "Superseded by ADR-<M>", write the new one).

You do NOT need an ADR for:
- Pure implementation details (variable names, code formatting).
- Reversible code changes.
- Things fully covered by the project-level rules/guides.

---

## Example: AniKoto's decision to use the `/filter?keyword=` endpoint

(For reference — this is the KIND of decision worth an ADR.)

> **Context:** the autosuggest endpoint (`/ajax/anime/search`) returns HTTP 500. Search is broken.
>
> **Decision:** use the paginated `/filter?keyword=<query>&<filters>&page=<page>` endpoint for ALL
> searches. It supports search + filters together, 30 results/page, paginated.
>
> **Rationale:** the filter endpoint is more capable (filters + search together) and works reliably.
> The autosuggest endpoint is broken on the site. Even if it worked, it returns only 5 results —
> insufficient for a catalog search.
>
> **Consequences:**
> - Positive: search works; filters + search compose; pagination works.
> - Negative: slightly slower than an autosuggest (full page load vs AJAX) — acceptable.
>
> **Alternatives:**
> 1. Fix the autosuggest endpoint — rejected (it's a site-side 500, can't fix).
> 2. Use a different AJAX endpoint — rejected (none found that work).
>
> **References:** `../sites/getsources-migration-and-id-analysis.md`, session 42 log.
