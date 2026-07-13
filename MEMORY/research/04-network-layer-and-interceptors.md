# Aniyomi Network Layer & Interceptors

> Last updated: 2026-06-22 · Status: VERIFIED
> Source: `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../network/` (compile-time stubs) +
> `SHARED/REFERENCE_HUB/aniyomi-app/core/common/src/main/java/eu/kanade/tachiyomi/network/` (runtime impls) +
> `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/{cloudflareinterceptor,cookieinterceptor,randomua,textinterceptor,synchrony,zipinterceptor}/` (yuzono lib modules).
>
> Every extension makes HTTP requests and most deal with Cloudflare/cookies/rate-limiting, so this
> is fundamental. This documents both what you compile against (the ext-lib v16 stubs) and what
> actually runs (the app's `core/common` impls), plus yuzono's reusable interceptor `lib/` modules.

---

## 1. The `NetworkHelper` — your entry point to the shared OkHttpClient

**Compile-time stub:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt`
```kotlin
class NetworkHelper(context: Context) {
    val client: OkHttpClient = throw Exception("Stub!")                                       // L10
    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient = throw Exception("Stub!")                             // L16
    fun defaultUserAgentProvider(): String = throw Exception("Stub!")                          // L21
}
```

**Runtime impl:** `SHARED/REFERENCE_HUB/aniyomi-app/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt:15-80`

The real `NetworkHelper(context, preferences)` builds `client` with:
- `cookieJar = AndroidCookieJar()` (wraps `android.webkit.CookieManager` — shared with WebView)
- `connectTimeout(30s)`, `readTimeout(30s)`, `callTimeout(2min)`
- `Cache` at `cacheDir/network_cache`, 5 MiB
- Interceptors (in order):
  1. `UncaughtExceptionInterceptor` — wraps non-IOException as IOException
  2. `UserAgentInterceptor(::defaultUserAgentProvider)` — injects default UA when missing
  3. **`CloudflareInterceptor`** — only on `client` (NOT on `nonCloudflareClient`)
  4. (network) `IgnoreGzipInterceptor` — strips `Accept-Encoding: gzip` so brotli handles it
  5. (network) `BrotliInterceptor` — brotli + gzip decoding
  6. (network, optional) `HttpLoggingInterceptor` HEADERS — only when verbose logging on
- DoH provider (13 options: Cloudflare, Google, AdGuard, Quad9, AliDNS, DNSpod, 360, Quad101, Mullvad, ControlD, Njalla, Shecan, LibreDNS) based on user pref
- `defaultUserAgentProvider()` returns the user-configured default UA (Firefox 136 on Win10 by default)

> **KEY:** `network.client` already has Cloudflare baked in. You do NOT need to add Cloudflare
> handling yourself for most sites. Only use yuzono's `lib/cloudflareinterceptor` if the built-in
> one is insufficient (or for forks that stripped it).

You access `network` via `AnimeHttpSource.network` (protected, inherited). The canonical client-override pattern:
```kotlin
override val client = network.client.newBuilder()
    .addInterceptor(MyInterceptor())
    .build()
```
**Never** construct a fresh `OkHttpClient.Builder()` — you'd lose all the app defaults (Cloudflare, UA, cache, DoH, cookies).

---

## 2. Request builders — `GET` / `POST` / suspend shortcuts

**Compile-time:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../network/Requests.kt` (all stubs)

| Function | Signature | Since |
|---|---|---|
| `GET` | `fun GET(url: String, headers: Headers = DEFAULT_HEADERS, cache: CacheControl = DEFAULT_CACHE_CONTROL): Request` | — |
| `GET` | `fun GET(url: HttpUrl, headers: Headers = DEFAULT_HEADERS, cache: CacheControl = DEFAULT_CACHE_CONTROL): Request` | ext-lib 14 |
| `POST` | `fun POST(url: String, headers: Headers = DEFAULT_HEADERS, body: RequestBody = DEFAULT_BODY, cache: CacheControl = DEFAULT_CACHE_CONTROL): Request` | — |
| `OkHttpClient.get` | `suspend fun OkHttpClient.get(url: String, headers: Headers = DEFAULT_HEADERS, cache: CacheControl = DEFAULT_CACHE_CONTROL): Response` | **ext-lib 16** |
| `OkHttpClient.get` | `suspend fun OkHttpClient.get(url: HttpUrl, headers: Headers = DEFAULT_HEADERS, cache: CacheControl = DEFAULT_CACHE_CONTROL): Response` | **ext-lib 16** |
| `OkHttpClient.post` | `suspend fun OkHttpClient.post(url: String, headers: Headers = DEFAULT_HEADERS, body: RequestBody = DEFAULT_BODY, cache: CacheControl = DEFAULT_CACHE_CONTROL): Response` | **ext-lib 16** |

**Defaults** (file-private): `DEFAULT_CACHE_CONTROL = maxAge=10 min`, `DEFAULT_HEADERS = empty`, `DEFAULT_BODY = empty FormBody`.

> ⚠️ The 10-minute default cache is surprising. Pass `CacheControl.FORCE_NETWORK` for fresh data.

The suspend `get`/`post` shortcuts are trivial: `newCall(GET(...)).awaitSuccess()`. They're the modern ext-lib 16 entry points (no RxJava).

`PUT`/`DELETE` exist in the app runtime but are NOT in the published v16 stubs (so don't use them in v16 extension code — build the `Request` manually with `Request.Builder()`).

---

## 3. OkHttp suspend bridges — `await` / `awaitSuccess`

**Compile-time:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../network/OkHttpExtensions.kt`

| Function | Signature | Purpose |
|---|---|---|
| `Call.await` | `suspend fun Call.await(): Response` | Coroutine bridge; preserves caller stack trace |
| `Call.awaitSuccess` | `suspend fun Call.awaitSuccess(): Response` | Same, throws `HttpException(code)` on non-2xx |
| `HttpException` | `class HttpException(val code: Int) : IllegalStateException("HTTP error $code")` | Standardized HTTP error |

The app's real impl (`core/common/.../network/OkHttpExtensions.kt:102-118`) captures the caller's stack trace so coroutine exceptions point at your extension code, not the OkHttp thread. **`awaitSuccess()` is the primary path used by ext-lib 16 `AnimeHttpSource`.**

The app also has `Response.parseAs<T>()` (`context(Json) inline fun <reified T> Response.parseAs(): T`, line 135) but it's NOT in the published v16 stub — use `keiyoushi.utils.parseAs` from `core/` instead (same thing, re-exported).

---

## 4. Jsoup — `Response.asJsoup()`

**Compile-time:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../util/JsoupExtensions.kt:11`
```kotlin
fun Response.asJsoup(html: String? = null): Document { throw Exception("Stub!") }
```
Note: it's in the `util/` package, NOT `network/`. Parses the response body as a Jsoup `Document`. Use `response.useAsJsoup()` from `keiyoushi.utils` (which does `use { it.asJsoup() }` to auto-close) — see `MEMORY/research/05-keiyoushi-utils-core.md` §4.

---

## 5. JavaScript engine — `JavaScriptEngine` (QuickJS wrapper)

**Compile-time:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../network/JavaScriptEngine.kt`
```kotlin
class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T = throw Exception("Stub!")    // L19
}
```

**Runtime impl** (`core/common/.../network/JavaScriptEngine.kt:1-26`):
```kotlin
class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T = withIOContext {
        QuickJs.create().use { it.evaluate(script) as T }   // L22
    }
}
```

**Lifecycle:** each `evaluate()` call creates a fresh `QuickJs` via `QuickJs.create().use { ... }` (auto-closed). The `JavaScriptEngine` class holds no QuickJs reference and has **no `close()` method**. You can construct `JavaScriptEngine(applicationContext)` per-call and let it GC.

**Use case:** evaluate obfuscated JS from a site to extract the real video URL / token. For de-obfuscating whole scripts (e.g. CF-protected), yuzono's `lib/synchrony` is a higher-level option (see §8.5).

---

## 6. Rate limiting — `rateLimit` / `rateLimitHost` (DEPRECATED in v16, but still work)

**Compile-time:** `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../network/interceptor/{RateLimitInterceptor,SpecificHostRateLimitInterceptor}.kt`

Both `rateLimit` and `rateLimitHost` are marked `@Deprecated` in the ext-lib v16 stubs. The kotlin.time overload even has `replaceWith = ReplaceWith("this")` (i.e. "delete the call"). The deprecation message: "Default rate limiting implementation is no longer provided. Source developers are now responsible for implementing their own rate limiting logic if desired, to prevent forks from bypassing it."

**However**, the app's real `core/common/.../interceptor/RateLimitInterceptor.kt` still ships a working impl (only the legacy `TimeUnit` overload is deprecated in the app, NOT the kotlin.time one). So **calls still work today** — you just get a compile-time deprecation warning from the stub.

| Function | Signature | Applies to |
|---|---|---|
| `Builder.rateLimit` | `fun OkHttpClient.Builder.rateLimit(permits: Int, period: Long = 1, unit: TimeUnit = SECONDS): Builder` (deprecated) | ALL hosts |
| `Builder.rateLimit` | `fun OkHttpClient.Builder.rateLimit(permits: Int, period: Duration = 1.seconds): Builder` (deprecated in stub, NOT in app) | ALL hosts |
| `Builder.rateLimitHost` | `fun OkHttpClient.Builder.rateLimitHost(httpUrl: HttpUrl, permits: Int, period: Long = 1, unit: TimeUnit = SECONDS): Builder` (deprecated) | one host |
| `Builder.rateLimitHost` | `fun OkHttpClient.Builder.rateLimitHost(httpUrl: HttpUrl, permits: Int, period: Duration = 1.seconds): Builder` (deprecated in stub) | one host |
| `Builder.rateLimitHost` | `fun OkHttpClient.Builder.rateLimitHost(url: String, permits: Int, period: Duration = 1.seconds): Builder` (deprecated in stub) | one host |

**Algorithm** (app impl, `RateLimitInterceptor.kt:64-127`): `Semaphore(1, true)` fair lock + `ArrayDeque<Long>` of timestamps. Cached responses (`response.networkResponse == null`) don't count against the limit.

**When to use:** site 429s you or bans aggressive scraping. Common: Jikan (MyAnimeList API) hard 1 req/sec; AniList GraphQL prefers gentle pacing; some CDNs throttle.

**Future-proofing for v16:** either (a) accept the deprecation warning and use them anyway (works today, may break on forks), or (b) roll your own `Interceptor` with `Semaphore`/`ArrayDeque` (the app impl is a reference).

### Real yuzono example — `src/en/anikage/.../Anikage.kt`
```kotlin
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import kotlin.time.Duration.Companion.seconds

override val client = network.client.newBuilder()
    .rateLimitHost(baseUrl.toHttpUrl(), 5, 1.seconds)    // 5 req/sec to baseUrl host
    .build()
```

### Two-client pattern (when only a sub-API needs throttling) — `src/en/miruro/.../Miruro.kt`
```kotlin
private val jikanClient: OkHttpClient = network.client.newBuilder()
    .rateLimitHost("$JIKAN_API_URL/".toHttpUrl(), permits = 1, period = 1, unit = TimeUnit.SECONDS)
    .build()
```

---

## 7. Headers — `headers` / `headersBuilder()`

From the app's `AnimeHttpSource.kt:60-99`:
```kotlin
val headers: Headers by lazy { headersBuilder().build() }                    // L63
open val client: OkHttpClient get() = network.client                         // L68-69
protected open fun headersBuilder() = Headers.Builder().apply {
    add("User-Agent", network.defaultUserAgentProvider())                    // L97-99
}
```
`headers` is a lazy val built once from `headersBuilder()`. The default `headersBuilder()` only adds `User-Agent: <app default UA>`.

### Two canonical override patterns

```kotlin
// Pattern A — extend super (retains default User-Agent):
override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

// Pattern B — replace entirely (must set User-Agent yourself):
override fun headersBuilder() = Headers.Builder().add("Referer", baseUrl)
```

### Real examples

**Pattern A** — `src/en/allanime/.../AllAnime.kt`:
```kotlin
override fun headersBuilder() = super.headersBuilder().set("Referer", "$baseUrl/")
```

**Pattern B** — `src/en/hanime/.../Hanime.kt` (full custom):
```kotlin
override fun headersBuilder() = Headers.Builder()
    .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 ...")
    .add("Accept", "application/json")
    .add("Accept-Language", "en-US,en;q=0.9")
    .add("content-type", "application/json")
    .add("Origin", "https://hanime.tv")
    .add("Referer", "https://hanime.tv/")
```

> Set `Referer` / `Origin` when the host requires it — otherwise mpv's segment requests 403. For
> per-video headers (different from the source's default), set `Video.headers` (the player falls
> back to `source.headers` if `Video.headers` is null).

---

## 8. yuzono `lib/` interceptor modules

Base path: `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/`

### 8.1 `cloudflareinterceptor` — self-contained WebView Cloudflare bypass
**Constructor:** `class CloudflareInterceptor(private val client: OkHttpClient) : Interceptor` (`CloudflareInterceptor.kt:23`)
- Detection: `response.code in [403, 503] && response.header("Server") in ["cloudflare-nginx", "cloudflare"]`.
- Bypass: creates a WebView on the main looper, injects a `CloudflareJSI` JS interface, runs a `setInterval` script (`CHECK_SCRIPT`) that auto-clicks the challenge button + hcaptcha turnstile checkbox, waits up to 30s for `cf_clearance` cookie, then re-issues the request with the merged `Cookie` header. Pushes solved cookies into `client.cookieJar.saveFromResponse(...)`.
- **When to use:** only if the app's built-in `CloudflareInterceptor` (already in `network.client`) isn't enough, or for forks that stripped it. Most sites: you don't need this.
- Cookies stored in `android.webkit.CookieManager` (shared with the app's `AndroidCookieJar`).

### 8.2 `cookieinterceptor` — force-set cookies per domain
**Constructor:** `CookieInterceptor(domain: String, cookies: List<Pair<String, String>>)` or single-cookie `CookieInterceptor(domain: String, cookie: Pair<String, String>)` (`CookieInterceptor.kt:7-11`)
- Init block immediately persists cookies via `CookieManager.getInstance().setCookie("https://$domain/", "$k=$v; Domain=$domain; Path=/")`.
- `intercept`: if `request.url.host` ends with `domain` and not all cookies are already in the request's `Cookie` header, re-sets them and proceeds with a merged header.
- **When to use:** session/consent cookies the site expects but doesn't set via `Set-Cookie`.
- Usage: `.addInterceptor(CookieInterceptor("example.com", "session" to "abc123"))`.

### 8.3 `randomua` — rotate real-world User-Agents
**API** (`Helper.kt` + `UserAgentPreference.kt`):
- `context(source: HttpSource) fun Headers.Builder.setRandomUserAgent(userAgentType: UserAgentType? = null, filterInclude: List<String> = emptyList(), filterExclude: List<String> = emptyList()): Headers.Builder` (`UserAgentPreference.kt:34-54`)
- `context(source: HttpSource) fun PreferenceScreen.addRandomUAPreference()` (`UserAgentPreference.kt:59-92`)
- `internal fun getRandomUserAgent(userAgentType: UserAgentType, filterInclude: List<String>, filterExclude: List<String>): String?` (`Helper.kt:31-71`)
- `enum class UserAgentType { MOBILE, DESKTOP, OFF }`
- Fetches `https://keiyoushi.github.io/user-agents/user-agents.json` (cached 24h), filters by include/exclude substrings, returns `.randomOrNull()`. Uses `runBlocking(Dispatchers.IO)` so callable from non-suspend `headersBuilder()`.
- **⚠️ Spotless `RandomUACheck` bug:** the build-logic check (`PluginSpotless.kt:67-77`) looks for `override fun getMangaUrl(` but the anime API method is `getAnimeUrl(`. Grep confirms `getMangaUrl` appears in 0 files under `src/`. This check is a copy-paste leftover from the manga repo and **never fires** for anime extensions. If you adopt `randomua`, you MUST manually override `getAnimeUrl()` — the check won't enforce it.
- Currently 0 consumers in `src/` (built-in UA + site-specific UAs are preferred).

### 8.4 `textinterceptor` — render error/info as a PNG "video" page
**Constructor:** `class TextInterceptor : Interceptor` (no args) (`TextInterceptor.kt:33`)
- Detection: `url.host == "tachiyomi-lib-textinterceptor"` (constant `HOST`).
- Mechanism: takes `url.pathSegments[0]` (HTML-encoded heading) + `pathSegments[1]` (body), un-escapes, lays out with `StaticLayout` (heading 36f bold, body 30f), draws onto a white `Bitmap`, encodes as PNG, returns synthetic `Response(code=200, body=png, content-type=image/png)`.
- **Helper:** `TextInterceptorHelper.createUrl(title: String, text: String): String` → `"http://tachiyomi-lib-textinterceptor/<encoded-title>/<encoded-text>"`.
- **When to use:** surface an error/info message as a "video" so the user sees something instead of a blank screen.

### 8.5 `synchrony` — JS de-obfuscator (QuickJS sandbox)
**API:** `object Deobfuscator { fun deobfuscateScript(source: String): String? }` (`Deobfuscator.kt:10-32`)
- Loads `/assets/synchrony-v2.4.5.1.js`, patches the `export{...}` line (QuickJS doesn't support ES module imports), JSON-encodes the input, creates a fresh `QuickJs.create().use { ... }`, evaluates `new Deobfuscator().deobfuscateSource(<json>)`.
- **When to use:** sites that ship obfuscated JS containing the real video URL. Real usage: `src/id/nimegami/NimeGami.kt` (`import keiyoushi.lib.synchrony.Deobfuscator as Synchrony`).
- Object singleton, no state, no `close()`.

### 8.6 `zipinterceptor` — manga `.zip#page` unzipper (mostly irrelevant for anime)
**Class:** `open class ZipInterceptor` (no args; subclass and override `intercept` to call `zipImageInterceptor(chain)`) (`ZipInterceptor.kt:126`)
- Unzips `.zip` responses, decodes each image entry (AVIF/base64-in-SVG), stitches vertically into one tall JPEG. Manga-only helper — anime extensions won't need this.

---

## 9. Network helper API quick reference

| Class / Function | Signature | Purpose |
|---|---|---|
| `NetworkHelper.client` | `val client: OkHttpClient` | Shared client with Cloudflare + UA + brotli + cache + DoH + cookies baked in |
| `NetworkHelper.defaultUserAgentProvider()` | `fun defaultUserAgentProvider(): String` | User-configured default UA (Firefox 136 / Win10 default) |
| `Call.awaitSuccess()` | `suspend fun Call.awaitSuccess(): Response` | Suspend bridge; throws `HttpException(code)` on non-2xx |
| `HttpException` | `class HttpException(val code: Int) : IllegalStateException` | Standardized HTTP error |
| `GET` | `fun GET(url: String, headers, cache): Request` | Build a GET (default cache = maxAge 10 min) |
| `POST` | `fun POST(url: String, headers, body, cache): Request` | Build a POST (default body = empty FormBody) |
| `OkHttpClient.get` | `suspend fun OkHttpClient.get(url: String, headers, cache): Response` | ext-lib 16 suspend GET shortcut |
| `OkHttpClient.post` | `suspend fun OkHttpClient.post(url: String, headers, body, cache): Response` | ext-lib 16 suspend POST shortcut |
| `JavaScriptEngine` | `class JavaScriptEngine(context: Context)` + `suspend fun <T> evaluate(script: String): T` | QuickJS wrapper; per-call `QuickJs.create().use { }` lifecycle |
| `Response.asJsoup` | `fun Response.asJsoup(html: String? = null): Document` | Parse body as Jsoup Document (in `util/`, not `network/`) |
| `Builder.rateLimit` | `fun OkHttpClient.Builder.rateLimit(permits: Int, period: Duration = 1.seconds): Builder` | Rate-limit ALL hosts (deprecated in v16 stub, works in app) |
| `Builder.rateLimitHost` | `fun OkHttpClient.Builder.rateLimitHost(url: String, permits: Int, period: Duration = 1.seconds): Builder` | Rate-limit one host (deprecated in v16 stub, works in app) |
| `AnimeHttpSource.client` | `open val client: OkHttpClient` (default: `network.client`) | Per-source override point |
| `AnimeHttpSource.headers` | `val headers: Headers` (lazy from `headersBuilder().build()`) | Default headers for all source requests |
| `AnimeHttpSource.headersBuilder()` | `protected open fun headersBuilder(): Headers.Builder` | Override to add Referer/Origin/etc. |

## 10. Interceptor module quick reference (yuzono `lib/`)

| Lib | Constructor | When to use |
|---|---|---|
| `cloudflareinterceptor` | `CloudflareInterceptor(client: OkHttpClient)` | Built-in CF bypass insufficient / fork stripped it. Most sites: NOT needed. |
| `cookieinterceptor` | `CookieInterceptor(domain: String, cookies: List<Pair<String,String>>)` | Force-set session/consent cookies per domain. |
| `randomua` | (extension funcs `setRandomUserAgent` + `addRandomUAPreference`) | Rotate real-world UAs. **Must manually override `getAnimeUrl()`** (Spotless check is buggy). |
| `textinterceptor` | `TextInterceptor()` | Render error/info as a PNG "video" page. |
| `synchrony` | `Deobfuscator.deobfuscateScript(source: String)` (object) | De-obfuscate JS via Synchrony engine in QuickJS sandbox. |
| `zipinterceptor` | `ZipInterceptor()` (subclass) | Manga `.zip#page` — NOT for anime. |

---

## 11. Things I could NOT fully verify (honest notes)

1. **`rateLimit`/`rateLimitHost` future:** the v16 stub deprecation says "default impl no longer provided" and "to prevent forks from bypassing it." The app still ships a working impl, but future Aniyomi versions may remove it. For long-term stability, consider rolling your own `Interceptor` (reference: app's `RateLimitInterceptor.kt:58-128`).
2. **`randomua` zero adoption:** 0 importers in `src/`. Likely because built-in UA + site-specific UAs are preferred, and `randomua` is newer. It's sanctioned (in `CONTRIBUTING.md`) but unproven in this repo.
3. **`lib/cloudflareinterceptor` cookie handoff:** pushes cookies via `client.cookieJar.saveFromResponse(syntheticHttpUrl, cookies)`. Works with `AndroidCookieJar` (delegates to `CookieManager`), but I didn't verify whether the synthetic `http://<domain>` URL causes cookie-scope mismatch with the original `https://<domain>` request. The follow-up `createRequestWithCookies` does its own header-level merge, so this is belt-and-suspenders.
4. **`PUT`/`DELETE` in v16 stubs:** absent from the published v16 `Requests.kt`. The app runtime has them. If you need PUT/DELETE in a v16 extension, build the `Request` manually via `Request.Builder().url(...).put(body).build()` — don't rely on the helper.
5. **`JavaScriptEngine` on forks:** the class is a stub; the app provides QuickJS. Forks that don't ship QuickJS (or ship a different JS engine) would break `JavaScriptEngine` callers. Aniyomi proper ships it.

## 12. Related docs

- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — where `client`/`headers`/`headersBuilder()` live in the API.
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how `Video.headers` flows to mpv.
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — stub vs runtime (this file is the network-layer instance of that pattern).
- `MEMORY/research/05-keiyoushi-utils-core.md` — `keiyoushi.utils.{get, post, useAsJsoup, bodyString, parseAs}` (re-exports/wraps the ext-lib network funcs).
- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` — the `lib/` extractors (playlistutils etc.) that USE this network layer.