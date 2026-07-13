# 04 — The Custom `extensions.utils` Toolkit (vs keiyoushi/utils)

> Last updated: 2026-06-22 (session 10) · Status: VERIFIED by decompilation
> Per project rule §1: cross-check / understanding record. No code copied.

Both reference APKs use a **custom, self-rolled utility toolkit** in package `extensions.utils`
(NOT `keiyoushi.utils`). The v3 author created it as a minimal subset of keiyoushi/utils; the
v16.4 author expanded it slightly. This document compares it to `keiyoushi.utils` (which we have
documented in `MEMORY/research/05-keiyoushi-utils-core.md`) and informs our own utils decision.

## 1. Why a custom toolkit (not keiyoushi)?

The v3 author (`1118000`) chose to ship a **minimal hand-rolled toolkit** (~660 LOC, 8 files)
instead of depending on `keiyoushi.utils` (~2500+ LOC, 75+ files). Reasons inferred:
- **Size**: the v3 APK is 250 KB; pulling in all of keiyoushi.utils would bloat it.
- **Self-containment**: no external dependency, no version drift.
- **Sufficiency**: AnikotoFinal only needs a tiny subset (Source base, JSON parse/encode, prefs builders, lazy/delegate) — keiyoushi.utils' GraphQL/Protobuf/NextJs/ReactFlight/crypto/url-utils are all unused.

The v16.4 author (`salmanbappi`) **kept the custom toolkit** (expanded it with `NetworkKt` + `UrlUtils`) AND ALSO bundled keiyoushi.utils (75 files) — but the `Anikoto` class itself only imports from `extensions.utils`, NOT keiyoushi. The keiyoushi bundling appears to be **unused bloat** (likely a build-config mistake or transitive pull-in that R8 didn't strip).

## 2. The `extensions.utils.Source` base class (★ the most important piece)

**File**: `Source.java` (220 lines) · `public abstract class Source extends AnimeHttpSource implements ConfigurableAnimeSource`

A near-verbatim copy of `keiyoushi.utils.Source` — same shape, same lazy-init pattern, same legacy-override guardrails. **★ But with one critical difference: the v3/v16.4 Source already has correct ext-lib 16 signatures** for `videoListRequest(hoster)` and `videoListParse(response, hoster)` (keiyoushi's has the v14-broken `videoListRequest(episode)` / `videoListParse(response)` that we'd need to delete).

### Provided members (all lazy-injected via Injekt)

| Member | Type | Visibility | How obtained | Purpose |
|---|---|---|---|---|
| `context` | `Application` | `protected` | `injectLazy()` (Injekt) | App context (SharedPreferences, Toast) |
| `migration` | `(SharedPreferences) -> Unit` | `open` (default no-op) | direct field, overridable | Pref-migration hook, runs on first prefs access |
| `json` | `Json` | `public open` | `injectLazy()` (Injekt) | Shared Json instance (app-configured: `ignoreUnknownKeys=true`, etc.) |
| `preferences` | `SharedPreferences` | `public final` | `lazy { context.getSharedPreferences("source_" + id, 0); migration(prefs); prefs }` | Prefs scoped to this source's id |
| `handler` | `Handler` | `protected` | `lazy { Handler(Looper.getMainLooper()) }` | Main-looper handler for `displayToast` |

### Methods
- `displayToast(message: String, length: Int = Toast.LENGTH_SHORT)` — posts `Toast.makeText(...)` to the main looper via `handler.post { ... }`. Safe from background threads.

### Legacy-overrides guardrails (all throw `UnsupportedOperationException`)
Every legacy `*Request`/`*Parse` method is overridden to throw, forcing subclasses to implement the suspend `get*` methods directly:
- `popularAnimeRequest/Parse`, `latestUpdatesRequest/Parse`, `searchAnimeRequest/Parse`, `animeDetailsRequest/Parse`, `episodeListRequest/Parse`, `seasonListRequest/Parse`, `hosterListRequest/Parse`
- ★ `videoListRequest(hoster: Hoster)` + `videoListParse(response: Response, hoster: Hoster)` — **v16-correct signatures** (keiyoushi's has the broken v14 sigs).

## 3. File-by-file toolkit reference (v3 → v16.4 additions)

| File | v3 | v16.4 | Purpose |
|---|---|---|---|
| `Source.java` | 220 LOC | (same + minor) | Abstract base (see §2) |
| `PreferencesKt.java` | 261 LOC, 9 funcs | ~480 LOC, 13+ funcs | Preference-screen builders (see §4) |
| `PreferenceDelegate.java` | 96 LOC | (same) | Typed pref property delegate (ReadWriteProperty) |
| `LazyMutable.java` | 63 LOC | (same) | lazy + reassignable property delegate |
| `JsonKt.java` | 119 LOC, 5 funcs | (same) | `String.parseAs<T>`, `Response.parseAs<T>`, `T.toJsonString`, `T.toRequestBody`, `String.toJsonBody` |
| `CollectionsKt.java` | 42 LOC, 2 funcs | (same) | `firstInstance<T>()`, `firstInstanceOrNull<T>()` |
| `DateKt.java` | 28 LOC, 1 func | (same) | `SimpleDateFormat.tryParse(date): Long` (0L on failure) |
| `FormatKt.java` | 29 LOC, 1 func | (same) | `Long.formatBytes(): String` (unique to this toolkit) |
| `NetworkKt.java` | ❌ none | ★ NEW | `asJsoup(response)` + likely `get/post` shortcuts (v16.4 uses `NetworkKt.asJsoup`) |
| `UrlUtils.java` | ❌ none | ★ NEW | URL fixer helpers (likely `fixUrl`) |

## 4. PreferencesKt — preference-screen builders

9 functions in v3 (expanded in v16.4). All follow a `getXPreference` (construct without adding) + `addXPreference` (construct + add to screen) pair pattern:

| Builder | Purpose | Notable params |
|---|---|---|
| `getEditTextPreference` / `addEditTextPreference` | Text input pref | `inputType`, `validate: (String) -> Boolean` (false → disable OK), `validationMessage`, `allowBlank`, `getSummary: (String) -> String` |
| `getListPreference` / `addListPreference` | List selection pref | parallel `entries` (display) + `entryValues` arrays |
| `getSetPreference` / `addSetPreference` | Multi-select pref | `MultiSelectListPreference` |
| `getSwitchPreference` / `addSwitchPreference` | Toggle pref | `SwitchPreferenceCompat` (NOT deprecated `SwitchPreference`), `onChange` lambda |

**Common params**: `restartRequired: Boolean = false` (shows "Restart the app to apply the new setting." toast on change), `enabled: Boolean = true`.

`RESTART_MESSAGE = "Restart the app to apply the new setting."` — shared constant.

### PreferenceDelegate (typed pref property delegate)
`PreferenceDelegate<T>(prefs, key, default)` implements `ReadWriteProperty`. Dispatches by `default`'s runtime type: `String`→`getString`, `Integer`→`getInt`, `Long`→`getLong`, `Float`→`getFloat`, `Boolean`→`getBoolean`, `Set`→`getStringSet`. **v3 does NOT catch ClassCastException** (keiyoushi's does) — a stored type mismatch crashes. Simpler but less defensive.

**Usage in AnikotoFinal**: 4 delegated prefs: `preferredQuality` (default "720"), `preferredAudio` (default "SUB"), `titleLang` (default "en"), `prefetchBuffer` (default "10"). All `PreferenceDelegate<String>`.

### LazyMutable (lazy + reassignable)
Double-checked locking with `UninitializedValue` sentinel. `var x by LazyMutable { initialValue }` — lazy on first read, reassignable via `x = newValue`. Identical to keiyoushi's.

## 5. Comparison: v3 `extensions.utils` vs `keiyoushi.utils` vs our needs

| Capability | v3 `extensions.utils` | keiyoushi `utils` | What WE need for Anikoto |
|---|---|---|---|
| **Base class** | `Source` (v16-correct ★) | `Source` (2 v16-broken sigs to delete) | ★ use v3-style (correct sigs) |
| **JSON helpers** | 5 (`parseAs`×2, `toJsonString`, `toRequestBody`, `toJsonBody`) | 12 (+transform overloads, `JsonElement.parseAs`, `InputStream.parseAs`, `toJsonElement`, `jsonInstance`) | 5 (v3 set is sufficient) |
| **Preferences** | 9 funcs + `PreferenceDelegate` + `LazyMutable` | 13 funcs + `PreferenceDelegate` (with ClassCastException catch) + `LazyMutable` + `getPreferencesLazy` | 9 (v3 set is sufficient; add the ClassCastException catch for safety) |
| **Collections** | `firstInstance`, `firstInstanceOrNull` | same 2 | 2 |
| **Date** | `SimpleDateFormat.tryParse` | same | 1 |
| **Format** | `Long.formatBytes` ★ | none | 1 (nice for debug logs) |
| **Network** | ❌ none (v3) / `asJsoup`+? (v16.4) | 7 (`OkHttpClient.get`, `.post`, `Response.useAsJsoup`, `bodyString`, `commonEmpty*`) | `asJsoup` + `get`/`post` suspend shortcuts (convenient) |
| **UrlUtils** | ❌ none (v3) / `fixUrl` (v16.4) | 1 object, 2 `fixUrl` overloads | 1 `fixUrl` (for relative-URL resolution) |
| **Coroutines/parallel** | ❌ none | 11 (`parallelMap`, `parallelFlatMap`, `parallelCatching*`, `*Blocking`) | ❌ not needed (we use `coroutineScope { async {...} }` directly) |
| **Crypto** | ❌ none | 5 (`decodeHex`, `toHex`, `rc4`, etc.) | ★ need `rc4` (we'll write our own AnikotoRC4) |
| **Context** | folded into `Source.context` | `applicationContext` top-level val | folded into Source |
| **Protobuf** | ❌ none | 9 exports | ❌ not needed |
| **GraphQL** | ❌ none | 12 exports + `GraphQLErrorInterceptor` | ❌ not needed |
| **NextJs / ReactFlight** | ❌ none | 9 exports + `reactFlight/` | ❌ not needed |
| **Total LoC** | ~660 | ~2500+ | ~700 (v3 + NetworkKt.asJsoup + ClassCastException catch) |

## 6. What this means for OUR extension

Our session-08 build compiles ext-lib v16 **stubs from source** (copied into our `src/main/kotlin/`) and does NOT use a `Source` base class — `Anikoto.kt` extends `AnimeHttpSource` directly. This works but misses the convenience of `Source` (preferences, JSON, toast, migration).

**Decision (informed by this analysis)**: see `MEMORY/decisions/03-best-method-to-build-extensions.md`. Summary:
- **Adopt the v3 `extensions.utils` toolkit pattern** — a minimal, self-rolled toolkit (~700 LOC) with `Source` base + JSON×5 + Preferences×9 + `PreferenceDelegate` + `LazyMutable` + `Collections`×2 + `Date`×1 + `Format`×1 + `NetworkKt.asJsoup` + `UrlUtils.fixUrl`.
- **Use v16-correct `Source` signatures** (the v3 `Source` already has them — copy that pattern, not keiyoushi's v14-broken one).
- **Add the ClassCastException catch** to `PreferenceDelegate` (keiyoushi's defensive pattern) — safer than v3's.
- **Do NOT pull in all of keiyoushi.utils** — it's 4× the code we need, and the v16.4 APK demonstrates it becomes unused bloat.
- **Write our own `AnikotoRC4`** (~15 LOC) rather than depending on keiyoushi's `rc4` — we need the specific key `"simple-hash"` anyway, and a self-contained crypto object is clearer.

This keeps our extension **lean** (matching our 80 KB build) and **self-contained** (no external toolkit dependency to drift or bundle).
