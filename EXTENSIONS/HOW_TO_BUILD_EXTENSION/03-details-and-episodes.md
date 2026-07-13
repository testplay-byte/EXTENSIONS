# Step 3 — Details and Episodes

> **Implement the episode list layer.** By the end of this step, a debug APK loads the anime
> details page AND the episode list (with sub/dub shown via the scanlator field, and an
> EpisodeMeta encoding chosen for fork compatibility).
>
> **Prerequisite:** Step 2 complete (catalog + details page work).
> **Done when:** details page shows full info; episode list loads with correct sub/dub labeling;
> EpisodeMeta encoding is decided + documented; a session log is written.

---

## 3.1 Confirm the episode-list mechanism (from Step 1)

Re-check your Step 1 §1.2 findings on episodes:

- [ ] Is the episode list **on the detail page** (HTML), or fetched via a **separate AJAX endpoint**?
- [ ] If AJAX: what's the endpoint, method (GET/POST), params (anime id? page?), and response shape (HTML fragment? JSON?)?
- [ ] Does it **paginate** (many episodes split across pages)? If so, how?
- [ ] What identifies each episode? (number? title? air date? a thumbnail? sub/dub availability?)

> **AniKoto reference:** AniKoto fetches episodes via `/ajax/episode/list/{id}` (AJAX HTML fragment), paginated. See `../anikoto/MEMORY/sites/endpoints.md` + `../anikoto/MEMORY/modules/02-anime-details-episodes.md`.

---

## 3.2 Implement episodeListParse

- [ ] Build the episode-list request (either reuse the details response, or a separate AJAX call).
- [ ] Parse each episode into an `SEpisode`:
  ```kotlin
  override fun episodeListParse(response: Response): List<SEpisode> {
      return <select episode elements>.map { element ->
          SEpisode.create().apply {
              episode_number = <parse number>.toFloat()
              name = <clean name — just number + title, no brackets/tags>  // rule §8
              url = <episode url or encoded EpisodeMeta>  // §3.4
              date_upload = <parse date if available>
              scanlator = <SUB / HSUB / DUB — rule §8>  // §3.3
          }
      }
      // ★ Return in DESCENDING order (ep N first, ep 1 last).
      // Aniyomi displays episodes in REVERSE of the returned order (latest at top by default).
      // Returning descending → Aniyomi reverses → user sees ascending (ep 1 first).
      // Do NOT return ascending — that causes episodes to display reversed (13→1).
      // See common-pitfalls.md §episode-order-reversed (MKissa session 03).
  }
  ```
- [ ] Build + install + verify: tap an anime → episode list loads, in ascending display order (ep 1 first).

> **AniKoto reference:** `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §episodeListParse.

---

## 3.3 Sub/dub via the scanlator field (rule §8)

**Rule §8:** sub/dub availability goes in the **scanlator field**, NOT crammed into the episode
name. Episode names stay clean (just number + title).

- [ ] For each episode, determine its audio type(s) from the site (Step 1 §1.5).
- [ ] If an episode has MULTIPLE audio types (SUB + DUB), you have two options:
  - **Option A (one episode per audio type):** emit two `SEpisode`s for the same episode number — one with `scanlator = "SUB"`, one with `scanlator = "DUB"`. The episode names differ slightly (e.g. `"Ep 1 - title (SUB)"` vs `"(DUB)"`) OR are identical and the scanlator field disambiguates.
  - **Option B (one episode, list audio in scanlator):** emit one `SEpisode` with `scanlator = "SUB, DUB"` and let the video list (Step 4) offer both.
  - AniKoto uses **Option A** (separate episodes per audio type). Choose based on the site's structure.
- [ ] Verify the labels match the site's own terminology (rule §7). Don't show "H-Sub" if the site says "Sub".
- [ ] Build + install + verify: episode list shows sub/dub cleanly below the episode name.

> **AniKoto reference:** `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §scanlator. AniKoto's audio-type analysis: `../anikoto/MEMORY/sites/audio-types.md`.

---

## 3.4 Decide the EpisodeMeta encoding (★ fork compatibility)

The `episode.url` field is what Aniyomi passes back to `getVideoList()`. In **legacy-pipeline
forks** (older Aniyomi/Animiru), the app tries to DNS-resolve `episode.url` as a URL before calling
your extractor — if it's not a valid URL, you get a "DNS error" and playback fails.

**The fix (AniKoto session 43):** encode the episode URL as a **valid URL path** with metadata in
the **fragment**:

```
/watch/<slug>/ep-<N>#<url-encoded-metadata>
```

- The path (`/watch/<slug>/ep-<N>`) is a valid URL → DNS resolves to `baseUrl` → no error.
- The fragment (`#...`) carries whatever metadata your `getVideoList()` needs (anime id, episode id,
  server list path, etc.). Fragments aren't sent to the server, so they're free-form.

In `getVideoList()`, decode the fragment to recover the metadata.

- [ ] Decide what metadata `getVideoList()` will need (anime id? episode id? server path?).
- [ ] Encode it into the fragment: `episode.url = "/watch/<slug>/ep-$num#${URLEncoder.encode(meta, "UTF-8")}"`.
- [ ] Document the encoding in `MEMORY/modules/02-anime-details-episodes.md` (or your equivalent).
- [ ] **Also implement a legacy decoder** that handles the OLD pipe-delimited format
  (`animeId|episodeId|...`) — in case any saved anime has the old format. AniKoto did this.

> **AniKoto reference (★ critical):** `../anikoto/MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md` — the full symptom→cause→fix→verification for the fork-compat EpisodeMeta encoding.
> **AniKoto reference:** `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §EpisodeMeta.

---

## 3.5 Implement the legacy getVideoList(SEpisode) override (fork compat)

In addition to the ext-lib 16 `getHosterList(episode)` method, **override the legacy
`getVideoList(episode: SEpisode)`** so legacy-pipeline forks can call it:

```kotlin
// ★ Fork compatibility — legacy-pipeline forks call getVideoList(SEpisode), not getHosterList.
// Delegate to getHosterList + flatten the result.
override fun getVideoList(episode: SEpisode): List<Video> {
    return try {
        getHosterList(episode).flatMap { it.videos }
    } catch (e: Exception) {
        AnikotoLog.e("getVideoList legacy fallback failed", e)
        emptyList()
    }
}
```

- [ ] Implement this override (delegate to `getHosterList` + flatten).
- [ ] This ensures both new-pipeline (Aniyomi latest) and legacy-pipeline (older forks) extensions work.

> **AniKoto reference:** `../anikoto/MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md` + `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §legacy-getVideoList.

---

## 3.6 Episode metadata enrichment (OPTIONAL — can defer)

AniKoto + AnimePahe + MKissa all enrich episodes with thumbnails + titles + descriptions from
external sources. This is a nice-to-have, not required for v1.

- [ ] If you want this, defer to after Step 5 (release). It's an enhancement.
- [ ] **Two approaches** (pick based on what the site provides):
  - **MAL ID approach** (AniKoto + AnimePahe): extract `myanimelist.net/anime/<id>` from the detail page's external links → use Jikan + AniList + Anikage + Kitsu multi-source. Reference: [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md).
  - **AniList ID approach** (MKissa): if the site uses AniList-hosted thumbnails (URL contains `anilist.co/file/`), extract the AniList media ID via `bx(\d+)-` regex → use Anikage (primary) + Jikan (fallback). OkHttp-only, no WebView needed. Reference: `EXTENSIONS/mkissa/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`.
- [ ] The AniList ID approach is simpler (one API call to Anikage gives all 3 metadata types) and works when no MAL links are available.

---

## 3.7 Verification checklist (Step 3 is done when ALL pass)

- [ ] Episode list loads (tap an anime → episodes appear in ascending display order, ep 1 first)
- [ ] ★ Episodes returned in DESCENDING order from the extension (Aniyomi reverses for display) — see [`common-pitfalls.md`](common-pitfalls.md) §episode-order-reversed
- [ ] Episode names are clean (just number + title, no brackets/tags) — rule §8
- [ ] Sub/dub shown via scanlator field (below the episode name) — rule §8
- [ ] Audio labels match the site's terminology — rule §7
- [ ] `episode.url` uses the fork-compat encoding (`/watch/<slug>/ep-N#fragment` or `/anime/<id>/p-N-sub#fragment`) — §3.4
- [ ] Legacy `getVideoList(SEpisode)` override implemented (delegates to `getHosterList`) — §3.5
- [ ] No crashes in logcat
- [ ] Episode-list parsing documented in `MEMORY/modules/02-anime-details-episodes.md`
- [ ] Write a session log in `MEMORY/session-logs/`

**Only when all pass → proceed to Step 4.**

---

## What to ask the user about (common Step 3 questions)

- "The site lists SUB and DUB as separate episodes. Should I emit one episode per audio type (AniKoto style) or one episode with both in scanlator?"
- "Episodes don't have air dates on the site. Leave `date_upload = 0L`?"
- "I'm encoding episode metadata as `/watch/<slug>/ep-N#<meta>`. Confirm this fork-compat approach?"
- "The episode list is AJAX-paginated (30 per page). For a 100-episode anime, I'll fetch all pages. OK?"
