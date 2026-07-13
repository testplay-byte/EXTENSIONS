# 00 — Philosophy and Rules

> **Read this before starting any step.** It defines the principles that govern HOW you build, and
> when to ask the user vs. decide yourself. Source: [`MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md)
> + 51 sessions of AniKoto development distilled.

---

## The four core principles

### 1. Verify before trusting
- Don't trust the user's claim about the site. Don't trust the reference APK. Don't trust a single API response.
- Use a **real browser** (`agent-browser`) to load pages and capture network requests. Curl alone misses JavaScript-rendered content, redirects, and headers.
- Test **ALL servers from ALL endpoints**. A site has multiple server-list paths — test each one's full resolve chain (server list → embed page → video URL → playable stream).
- If the user says "the site has feature X", go confirm it yourself before implementing.
- See: [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md) §1

### 2. One change at a time
- No bundled multi-fix changes. Each change → build → test → verify → next change.
- Why: bundled fixes caused regressions in early sessions. When 3 things change at once and it breaks, you can't tell which one.
- "Verify" means: the debug APK installs, the feature works end-to-end on a device/emulator, and logcat is clean of relevant errors.
- See: [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md) §2

### 3. Don't force anything
- If you can't handle something properly, **say so**. Tell the user "I couldn't resolve server X's WAF — here's what I tried, here's where I'm stuck".
- Revert rather than break. If a change breaks the build or a working feature, `git`/file-revert immediately; don't pile on fixes.
- Be honest about what you can and can't do. Don't claim success when you haven't verified.
- See: [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md) §2

### 4. Document everything
- Research findings → `MEMORY/sites/` or `MEMORY/research/`
- How-to guides → `MEMORY/guides/` (project-level) or this folder
- Architecture decisions → `MEMORY/decisions/` (ADR-style — see [`decision-log-template.md`](decision-log-template.md))
- Resolved issues → `MEMORY/issues-resolutions/` (symptom → cause → fix → verification)
- Session logs → `MEMORY/session-logs/` (one per working session, MANDATORY)
- Raw/unverified notes → `MEMORY/TEMPORARY_MEMORY/` (promote out when verified — move, don't copy)
- See: [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md) §3, §4

---

## The two-tier memory model

Nothing goes directly into mature folders. Everything starts in `TEMPORARY_MEMORY/` and is
**promoted** (moved, not copied) only after verification:

```
   RAW / UNVERIFIED                          VERIFIED / MATURE
   TEMPORARY_MEMORY/      ──verify──►        sites/  modules/  issues-resolutions/
   (drafts, hypotheses,                      research/  session-logs/  decisions/
    raw notes, issues                         (move the file; delete the temp copy)
    under investigation)
```

A note is "verified" when confirmed against the live site / source code / a successful build.
Unresolved issues stay in `TEMPORARY_MEMORY/` until resolved — then promote the key finding +
resolution to `issues-resolutions/` and delete the temp.

---

## The iterative loop (within each step)

Every sub-task follows this loop. **Do not skip the verify phase.**

```
   1. OBSERVE     — load the site in agent-browser; capture network; read HTML/JSON
   2. HYPOTHESIZE — "I think endpoint X returns Y, with params A, B"
   3. TEST        — verify with curl + browser (does it actually return Y?)
   4. IMPLEMENT   — write the Kotlin code (one method at a time)
   5. BUILD+TEST  — ./gradlew :src:en:<name>:assembleDebug; install; click it
   6. VERIFY      — does it work end-to-end? (rule §1)
                    YES → promote notes, write session log, next sub-task
                    NO  → debug (adb logcat -s <Tag>), revert if needed, re-observe,
                          ASK THE USER if stuck
```

---

## When to ask the user (don't guess these)

**Ask the user — do NOT guess — in these situations:**

| Situation | Why ask | What to provide in the ask |
|---|---|---|
| **Live domain is ambiguous** (site rotates domains) | Guessing = scraping a dead/mirror domain | The candidate domains you found + which one agent-browser confirmed works |
| **Site behavior is genuinely ambiguous** (e.g. is "HSUB" labeled "Sub"?) | The site is inconsistent; user preference matters | What you observed + the options + your recommendation |
| **Endpoint returns errors you can't resolve** by testing alternatives | You've hit a wall; the user may know site quirks | What you tried + the exact error + the endpoint |
| **About to change something irreversible** (`versionId`, package, extClass) | These affect saved-anime / update compat | The proposed change + WHY + the consequence |
| **A video server is WAF-blocked and WebView can't pass** | Don't silently drop a server | Which server + what you tried (WebView, headers, cookies) |
| **Bug vs. intentional behavior unclear** (e.g. duplicate videos across audio types) | Could be dedup needed or a real bug | What you observed + whether AniKoto hit the same |
| **Fork-compatibility question** (legacy vs new pipeline) | Affects which code paths to use | The scenario + how AniKoto handled it (see [`reference-prior-solutions.md`](reference-prior-solutions.md) §fork-compat) |
| **You're about to add a NEW dependency or lib module** | May conflict with existing build | What you want to add + why + alternatives |
| **Site requires solving a captcha / login** to access content | Can't/shouldn't bypass automatically | What's blocking + the URL |

**How to ask well:** state what you observed, what you tried, what you think the options are, and
your recommendation. Don't just say "it doesn't work, what do I do?" — show your work.

---

## When to decide yourself (don't over-ask)

Decide yourself (and document it) when:

- The site behavior is clear and unambiguous from browser verification.
- It's a pure implementation detail (variable names, code structure) with no user-facing impact.
- AniKoto already solved the same problem — reuse the pattern (see [`reference-prior-solutions.md`](reference-prior-solutions.md)).
- It's a reversible code change (you can always revert).

Document the decision in a session log or ADR so future sessions know why.

---

## The "don't mess up the environment" rule

- The shared toolchain (`JDK/`, `ANDROID_SDK/`, `.android-env.sh`) is used by ALL extensions. Don't modify it for one extension.
- `SHARED/REFERENCE_HUB/` is read-only. Never edit reference repo files.
- Each extension's `DEV/` is self-contained — don't make one extension's build depend on another's files.
- If a build breaks the environment, revert rather than pile on fixes (rule §3).

---

## Logging discipline

- **All logs go to Android logcat** (tag = your extension's name, e.g. "Animepahe"). Capture with `adb logcat -s Animepahe:*`.
- No file logging to the user's device (no `WRITE_EXTERNAL_STORAGE`). AniKoto learned this in session 46 — logcat is sufficient for a mature extension.
- Use a truncation helper for long strings (URLs, tokens, response bodies) to avoid logcat's 4KB line limit. See how AniKoto did it: [`reference-prior-solutions.md`](reference-prior-solutions.md) §logging.
- Make logs detailed enough to pinpoint issues, not so noisy they drown the signal.

---

## Session logs are mandatory

At the end of EVERY working session, write a session log in `MEMORY/session-logs/`:

```
YYYY-MM-DD_session-NN_short-title.md
```

Include: the goal, what was done (concrete steps), what worked, what didn't, what's next. This is
how the next session picks up with zero context loss. Skipping this = losing everything you learned.

See AniKoto's session logs for the format: `../anikoto/MEMORY/session-logs/` (51 examples).
