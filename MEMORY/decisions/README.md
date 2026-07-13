# decisions/ — Architecture & Design Decisions (ADR-style)

> **Status: VERIFIED.** Records of decisions we committed to, with the reasoning. Read this to
> understand *why* the project is shaped the way it is.

## What goes here

- "We target ext-lib 16 because …"
- "We use multisrc theme X for site Y because …"
- "We represent sub/hsub/dub via the scanlator field because …"
- Workflow revisions (when the process itself changes — log the reason and date).
- Tooling/build choices.

## What does NOT go here

- Step-by-step how-to → `guides/`.
- Bug fixes → `issues-resolutions/`.
- Raw research → `research/`.

## ADR template (suggested)

```markdown
# Decision: <title>

> Date: YYYY-MM-DD · Status: Accepted (or Superseded by <link>)

## Context
What problem we're solving, what constraints exist.

## Decision
What we chose to do.

## Rationale
Why this option over the alternatives.

## Consequences
What this enables and what it costs.
```

## Naming

`YYYY-MM-DD_decision-<short-title>.md` or `ADR-NN-<short-title>.md`.

## Current contents

1. **`01-use-aniyomiorg-extensions-lib-v16.md`** — ADR: depend on official
   `com.github.aniyomiorg:extensions-lib:v16` (NOT the komikku fork `bdc8184127` that yuzono uses).
   Context, rationale, consequences (Java 17, legacy-overrides deletion, `ParsedAnimeHttpSource`
   deprecation), alternatives, references.
2. **`02-workspace-folder-architecture.md`** — ADR: the folder architecture. **Revised 2027-06-28
   (session 52)** to the multi-extension `EXTENSIONS/` layout (per-extension self-contained folders
   with their own Gradle project, keystore, APKs, and knowledge base; shared resources in `MEMORY/`
   and `SHARED/`). The original single-extension `WORKSPACE/` design is preserved as historical
   context. Includes the full old→new migration mapping.
3. **`03-best-method-to-build-extensions.md`** — ★ ADR: the 12-point best method for building
   Anikoto-style extensions on ext-lib 16 (build system, minimal self-rolled `extensions.utils`
   toolkit, two-client split, `EpisodeMeta` pipe-encoding, RC4 vrf, index-based local proxy,
   PNG-strip, parallel Hoster resolution, `initialized=true` videos, sort+prefer, R8 release,
   defensive coding). Informed by the session-10 analysis of both reference APKs.
