# `keiyoushi.utils` Core Module — The Standard Extension Toolkit

> Last updated: 2026-06-22 · Status: VERIFIED
> Source: `SHARED/REFERENCE_HUB/anime-extensions-ref/core/src/main/kotlin/keiyoushi/utils/` (13 files + `reactFlight/` subpackage).
>
> Every extension uses these helpers. This documents the full public API. `core/` is auto-added as
> `implementation(project(":core"))` by the build-logic plugins (`PluginExtensionLegacy.kt:153`,
> `PluginLibrary.kt:43`, `PluginMultiSrc.kt:42`) — you never declare the dependency yourself.

---

## 0. `core/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}
android {
    namespace = "keiyoushi.core"
    buildFeatures { resValues = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    compileOnly(libs.bundles.common)   // aniyomi-lib + okhttp + jsoup + serialization + injekt + ...
    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
}
```
`core/` publishes only Kotlin code (no Android resources beyond 5 default launcher mipmaps referenced by `common/AndroidManifest.xml`).

---

## 1. `Json.kt` — JSON helpers (12 exports)

| Name | Signature | Purpose |
|---|---|---|
| `jsonInstance` | `val jsonInstance: Json = Injekt.get()` (L17) | Shared injected `Json` (app config: `ignoreUnknownKeys=true`, etc.) |
| `String.parseAs<T>` | `inline fun <reified T> String.parseAs(json: Json = jsonInstance): T` (L24) | Decode JSON string → `T` |
| `String.parseAs<T>` (transform) | `inline fun <reified T> String.parseAs(json: Json = jsonInstance, transform: (String) -> String): T` (L32) | Transform raw string first, then parse |
| `Response.parseAs<T>` | `inline fun <reified T> Response.parseAs(json: Json = jsonInstance): T` (L37) | Decode response body (auto-closes via `use`) |
| `Response.parseAs<T>` (transform) | `inline fun <reified T> Response.parseAs(json, transform: (String) -> String): T` (L47) | Transform raw body first (buffers into memory) |
| `JsonElement.parseAs<T>` | `inline fun <reified T> JsonElement.parseAs(json: Json = jsonInstance): T` (L56) | Decode a `JsonElement` tree |
| `InputStream.parseAs<T>` | `inline fun <reified T> InputStream.parseAs(json: Json = jsonInstance): T` (L63) | Decode a stream (auto-closes) |
| `T.toJsonString` | `inline fun <reified T> T.toJsonString(json: Json = jsonInstance): String` (L70) | Encode `T` → JSON string |
| `String.toJsonBody` | `fun String.toJsonBody(): RequestBody` (L75) | Wrap JSON string as `RequestBody` (`application/json`) |
| `T.toJsonRequestBody` | `inline fun <reified T> T.toJsonRequestBody(json: Json = jsonInstance): RequestBody` (L80) | Encode + wrap in one step |
| `T.toJsonElement` | `inline fun <reified T> T.toJsonElement(json: Json = jsonInstance): JsonElement` (L85) | Encode to `JsonElement` tree |

**Gotchas:**
- `Response.parseAs` is NOT suspend — it's a blocking `inline fun`. Safe inside suspend functions but blocks the calling thread.
- The no-transform `Response.parseAs` uses `decodeFromBufferedSource` (streaming). The transform overload reads the whole body into memory — prefer the no-transform one when you don't need to mutate.

---

## 2. `Protobuf.kt` — Protobuf helpers (9 exports)

| Name | Signature | Purpose |
|---|---|---|
| `protoInstance` | `val protoInstance: ProtoBuf = Injekt.get()` (L16) | Shared injected `ProtoBuf` |
| `ByteArray.decodeProto<T>` | `inline fun <reified T> ByteArray.decodeProto(proto: ProtoBuf = protoInstance): T` (L24) | Decode bytes → `T` |
| `T.encodeProto` | `inline fun <reified T : Any> T.encodeProto(proto: ProtoBuf = protoInstance): ByteArray` (L31) | Encode `T` → bytes (T must be non-null) |
| `Response.parseAsProto<T>` | `inline fun <reified T> Response.parseAsProto(proto: ProtoBuf = protoInstance): T` (L40) | Decode response body via `.body.bytes()` (buffers into memory) |
| `Response.parseAsProto<T>` (transform) | `inline fun <reified T> Response.parseAsProto(proto, transform: (ByteArray) -> ByteArray): T` (L50) | Transform bytes first |
| `ResponseBody.parseAsProto<T>` | `inline fun <reified T> ResponseBody.parseAsProto(proto: ProtoBuf = protoInstance): T` (L59) | For `ResponseBody` (no auto-close) |
| `T.toRequestBodyProto` | `inline fun <reified T : Any> T.toRequestBodyProto(proto, mediaType = PROTOBUF_MEDIA_TYPE): RequestBody` (L67) | Encode + wrap as `RequestBody` |
| `String.decodeProtoBase64<T>` | `inline fun <reified T> String.decodeProtoBase64(proto: ProtoBuf = protoInstance): T` (L76) | Base64-decode (`Base64.NO_WRAP`) then protobuf-decode |
| `T.encodeProtoBase64` | `inline fun <reified T : Any> T.encodeProtoBase64(proto: ProtoBuf = protoInstance): String` (L85) | Encode → bytes → Base64 (`Base64.NO_WRAP`) |

**Gotchas:** `encodeProto`/`encodeProtoBase64` are constrained `T : Any`. Base64 uses `android.util.Base64.NO_WRAP` (not `java.util.Base64`).

---

## 3. `GraphQL.kt` — GraphQL helpers (12 exports, 314 lines)

| Name | Signature (short) | Purpose |
|---|---|---|
| `GraphQLErrorInterceptor` | `class GraphQLErrorInterceptor : Interceptor` (L52) | OkHttp interceptor: on non-2xx, peek body, throw `GraphQLException` if it has GraphQL `errors` |
| `graphQLBody` (JsonElement vars) | `fun graphQLBody(query, operationName, variables: JsonElement?, extensions: JsonElement?, json): RequestBody` (L73) | Build POST body |
| `graphQLBody` (typed vars) | `inline fun <reified V : Any> graphQLBody(query, operationName, variables: V, extensions, json): RequestBody` (L98) | Typed-vars overload |
| `Builder.appendGraphQLParams` | `fun Builder.appendGraphQLParams(query, operationName, variables, extensions, json): Builder` (L124) | Append GraphQL params as URL query params (for GET) |
| `graphQLPost` | `fun graphQLPost(url, headers, query, operationName, variables, extensions, cache, json): Request` (L178) | Build GraphQL POST `Request` |
| `graphQLGet` | `fun graphQLGet(url, headers, query, operationName, variables, extensions, cache, json): Request` (L231) | Build GraphQL GET `Request` (params as URL query) |
| `persistedQueryExtension` | `fun persistedQueryExtension(hash: String, version: Int = 1): JsonElement` (L279) | Build APQ `extensions` JSON |
| `Response.parseGraphQLAs<T>` | `inline fun <reified T> Response.parseGraphQLAs(json): T` (L288) | Parse envelope, unwrap `data`, throw `GraphQLException` if `errors` non-empty, throw `IllegalStateException` if `data` is null |
| `String.parseGraphQLAs<T>` | `inline fun <reified T> String.parseGraphQLAs(json): T` (L302) | Same for raw JSON string |
| `GraphQLException` | `class GraphQLException(message: String) : Exception(message)` (L313) | Thrown by `parseGraphQLAs` + `GraphQLErrorInterceptor` |

**Example (typed vars + APQ):**
```kotlin
val req = graphQLPost(
    url = "$baseUrl/graphql",
    headers = headers,
    query = null,                                  // APQ: query omitted
    operationName = "SearchAnime",
    variables = SearchVars(query = q, page = page),
    extensions = persistedQueryExtension(hash = "<sha256>"),
)
val data: SearchData = client.newCall(req).awaitSuccess().parseGraphQLAs()
```

**Gotchas:** `parseGraphQLAs` throws `IllegalStateException("GraphQL response is missing the 'data' field")` if response has neither `data` nor `errors` — wrap in `runCatching` if your backend legitimately returns no `data`. Typed overloads constrain `V : Any`.

---

## 4. `Network.kt` — suspend OkHttp helpers (7 exports, marked `// TODO: Remove with ext lib 16`)

| Name | Signature | Purpose |
|---|---|---|
| `OkHttpClient.get` (String) | `suspend fun OkHttpClient.get(url: String, headers = DEFAULT_HEADERS, cache = DEFAULT_CACHE_CONTROL): Response` (L24) | GET via `newCall(GET(...)).awaitSuccess()` |
| `OkHttpClient.get` (HttpUrl) | `suspend fun OkHttpClient.get(url: HttpUrl, headers, cache): Response` (L30) | HttpUrl overload |
| `OkHttpClient.post` | `suspend fun OkHttpClient.post(url: String, headers, body = DEFAULT_BODY, cache): Response` (L36) | POST via `newCall(POST(...)).awaitSuccess()` |
| `Response.useAsJsoup` | `fun Response.useAsJsoup(): Document` (L43) | `use { it.asJsoup() }` — closes the response |
| `Response.bodyString` | `fun Response.bodyString(): String` (L45) | `use { it.body.string() }` — closes the response |
| `commonEmptyHeaders` | `@Deprecated val commonEmptyHeaders: Headers` (L59) | okhttp3 v5 compat (use `Headers.EMPTY`) |
| `commonEmptyRequestBody` | `@Deprecated val commonEmptyRequestBody: RequestBody` (L73) | okhttp3 v5 compat (use `RequestBody.EMPTY`) |

**Defaults:** `DEFAULT_CACHE_CONTROL = maxAge=10 min`, `DEFAULT_HEADERS = empty`, `DEFAULT_BODY = empty FormBody`.

> ⚠️ The 10-min default cache is surprising. Pass `CacheControl.FORCE_NETWORK` for fresh data.
> `useAsJsoup`/`bodyString` are NOT suspend — they block. Safe inside suspend functions.

---

## 5. `Coroutines.kt` — parallel collection helpers (11 exports)

All parallel helpers run on `withContext(Dispatchers.IO) { map { async { f(it) } }.awaitAll() }`. **No semaphore, no fixed parallelism limit** — OkHttp's dispatcher (default `maxRequests=64`, `maxRequestsPerHost=5`) provides the practical throttle for HTTP-bound work.

| Name | Signature | Returns | Catches? |
|---|---|---|---|
| `parallelMap` | `suspend inline fun <A,B> Iterable<A>.parallelMap(crossinline f: suspend (A) -> B): List<B>` (L15) | `List<B>` | no |
| `parallelMapBlocking` | `inline fun <A,B> Iterable<A>.parallelMapBlocking(crossinline f: suspend (A) -> B): List<B>` (L24) | `List<B>` | no (wraps `runBlocking`) |
| `parallelMapNotNull` | `suspend ... parallelMapNotNull(...): List<B>` (L31) | `List<B>` (nulls filtered) | no |
| `parallelMapNotNullBlocking` | `... Blocking(...)` (L40) | `List<B>` | no |
| `parallelFlatMap` | `suspend ... parallelFlatMap(...): List<B>` (L47) | `List<B>` | no |
| `parallelFlatMapBlocking` | `... Blocking(...)` (L56) | `List<B>` | no |
| `parallelCatchingFlatMap` | `suspend ... parallelCatchingFlatMap(...): List<B>` (L64) | `List<B>` | **yes** — logs `"Coroutines"`, returns `emptyList()` per failure |
| `parallelCatchingMapNotNull` | `suspend ... parallelCatchingMapNotNull(...): List<B>` (L83) | `List<B>` | **yes** — logs, returns `null` per failure |
| `parallelCatchingFlatMapBlocking` | `... Blocking(...)` (L102) | `List<B>` | yes |
| `catchingFlatMap` | `suspend ... catchingFlatMap(...): List<B>` (L110) | `List<B>` | **yes — sequential**, logs `"Collections"` |
| `flatMapCatching` | `inline fun <A,B> Iterable<A>.flatMapCatching(f: (A) -> Iterable<B>): List<B>` (L125) | `List<B>` | **yes — sequential, non-suspend**, logs `"Collections"` |
| `catchingFlatMapBlocking` | `... Blocking(...)` (L140) | `List<B>` | yes |

**Gotchas:**
- `*Blocking` variants use `runBlocking` — needed in non-suspend code paths (e.g. `videoListParse(response, hoster)` which is non-suspend in v16).
- "Catching" variants catch `Throwable` including `CancellationException` — a known coroutines anti-pattern. Yuzono uses them anyway; be aware.
- No `parallelCatchingMap` (non-NotNull) — for map-or-die use `parallelMap` and wrap yourself.
- `catchingFlatMap`/`flatMapCatching` are **sequential** despite the name — just `flatMap` with try/catch.

**Example (real, `AnimeLek.kt:77`):**
```kotlin
return document.select(videoListSelector()).parallelCatchingFlatMapBlocking { element ->
    val url = element.attr("data-ep-url")
    // ... returns List<Video>
}
```

---

## 6. `Preferences.kt` — the full preferences API (13 exports, 522 lines) ★

### 6.1 SharedPreferences access
| Name | Signature | Purpose |
|---|---|---|
| `AnimeHttpSource.getPreferences` | `inline fun AnimeHttpSource.getPreferences(migration: SharedPreferences.() -> Unit = {}): SharedPreferences` (L21) | Get prefs for `id` (source id), running `migration`. Key: `"source_$id"`, MODE_PRIVATE. |
| `AnimeHttpSource.getPreferencesLazy` | `inline fun AnimeHttpSource.getPreferencesLazy(crossinline migration: SharedPreferences.() -> Unit = {}) = lazy { getPreferences(migration) }` (L28) | Same but `Lazy<SharedPreferences>` — preferred for class properties |
| `getPreferences(sourceId)` | `fun getPreferences(sourceId: Long): SharedPreferences` (L35) | Top-level — for when you only have a `Long` source id |

**Example (very common):**
```kotlin
private val preferences by getPreferencesLazy()
private val preferences by getPreferencesLazy { migrateOldKey() }   // with migration
```

### 6.2 `LazyMutable<T>` (L38-71)
A `ReadWriteProperty<Any?, T>` that's a `lazy { initializer() }` you can later reassign.
```kotlin
override var baseUrl by LazyMutable { preferences.hostUrl }   // Jellyfin.kt:88
```

### 6.3 `PreferenceDelegate<T>` + `SharedPreferences.delegate` (L81-138)
A `ReadWriteProperty` backed by `SharedPreferences`. Supports `String`, `Int`, `Long`, `Float`, `Boolean`, `Set<*>` (as `Set<String>`), `null`. On `ClassCastException` returns the default.
```kotlin
fun <T> SharedPreferences.delegate(key: String, default: T) = PreferenceDelegate(this, key, default)
```
**Example (`FASELHD.kt:275-276`):**
```kotlin
private var SharedPreferences.customDomain by preferences.delegate(PREF_DOMAIN_CUSTOM_KEY, "")
private var SharedPreferences.quality       by preferences.delegate(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)
// usage:
val cd = preferences.customDomain     // get
preferences.quality = "1080"          // set
```
> ⚠️ KDoc warns: "Do not use this on a lib-multisrc module, as it will be initialized before the source is created, which will cause the preferences to be created with the wrong source id."

### 6.4 Preference-screen builders (L159-521)
Eight top-level functions on `PreferenceScreen` — `getX` returns the preference (without adding), `addX` adds it to the screen. Common params: `key, default, title, summary, restartRequired=false, enabled=true, onChange={_,_->true}, onComplete={}`. `restartRequired=true` shows a "Restart the app to apply" toast.

| Function | Signature (short) | Wraps |
|---|---|---|
| `get/addEditTextPreference` | `(key, default: String, title, summary, getSummary, dialogMessage, inputType, validate, validationMessage, restartRequired, enabled, onChange, onComplete): EditTextPreference` (L159-271) | `androidx.preference.EditTextPreference` (with optional `TextWatcher` validation that disables OK when invalid) |
| `get/addListPreference` | `(key, default: String, title, summary, entries: List<String>, entryValues: List<String>, restartRequired, enabled, onChange, onComplete): ListPreference` (L287-358) | `androidx.preference.ListPreference` |
| `get/addSetPreference` | `(key, default: Set<String>, title, summary, entries, entryValues, restartRequired, enabled, onChange, onComplete): MultiSelectListPreference` (L374-446) | `androidx.preference.MultiSelectListPreference` |
| `get/addSwitchPreference` | `(key, default: Boolean, title, summary, restartRequired, enabled, onChange, onComplete): SwitchPreferenceCompat` (L460-521) | `androidx.preference.SwitchPreferenceCompat` (NOT the older `SwitchPreference`) |

**Example (`FASELHD.kt:278-298`):**
```kotlin
override fun setupPreferenceScreen(screen: PreferenceScreen) {
    screen.addListPreference(
        key = PREF_QUALITY_KEY,
        title = "Preferred quality",
        entries = listOf("1080p", "720p", "480p", "360p"),
        entryValues = listOf("1080", "720", "480", "360"),
        default = PREF_QUALITY_DEFAULT,
        summary = "%s",
    )
    screen.addEditTextPreference(
        key = PREF_DOMAIN_CUSTOM_KEY,
        default = "",
        title = "Custom domain",
        dialogMessage = "Enter custom domain",
        summary = preferences.customDomain,
        getSummary = { it },
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        validate = { it.isBlank() || (it.toHttpUrlOrNull() != null && !it.endsWith("/")) },
        validationMessage = { "Invalid URL" },
    )
}
```

---

## 7. `Source.kt` — abstract base class ★ CRITICAL for ext-lib 16

**Full source (L18-54):**
```kotlin
abstract class Source :
    AnimeHttpSource(),
    ConfigurableAnimeSource {
    protected val context: Application by injectLazy()
    protected open val migration: SharedPreferences.() -> Unit = {}
    open val json: Json by injectLazy()
    val preferences: SharedPreferences by getPreferencesLazy { migration }
    protected val handler by lazy { Handler(Looper.getMainLooper()) }
    protected fun displayToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        handler.post { Toast.makeText(context, message, length).show() }
    }

    // TODO: Remove with ext lib 16
    override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) = throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun animeDetailsRequest(anime: SAnime) = throw UnsupportedOperationException()
    override fun animeDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun episodeListRequest(anime: SAnime) = throw UnsupportedOperationException()
    override fun episodeListParse(response: Response) = throw UnsupportedOperationException()
    override fun videoListRequest(episode: SEpisode) = throw UnsupportedOperationException()   // ❌ v16: won't compile
    override fun videoListParse(response: Response) = throw UnsupportedOperationException()    // ❌ v16: won't compile
}
```

### 7.1 Public/protected API
| Member | Purpose |
|---|---|
| `context: Application` (L21) | Injekt-injected app context |
| `migration: SharedPreferences.() -> Unit` (L23) | Override for pref migrations on first access |
| `json: Json` (L25) | Injekt-injected `Json` (override for custom config) |
| `preferences: SharedPreferences` (L27) | Lazily-loaded prefs for `this.id`; migration runs at first access |
| `handler` (L29) | Main-looper handler for `displayToast` |
| `displayToast(message, length)` (L31-35) | Posts a `Toast` to the main looper; safe from background threads |

### 7.2 ★★★ Legacy overrides & ext-lib 16 migration (VERIFIED) ★★★

Cross-checked each override against v16 `AnimeHttpSource.kt` (`SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/.../online/AnimeHttpSource.kt`):

| `Source.kt` override | v16 `AnimeHttpSource` method | Compiles on v16? |
|---|---|---|
| `popularAnimeRequest(page)` | `protected abstract fun popularAnimeRequest(page: Int): Request` (v16 L119) | ✅ yes |
| `popularAnimeParse(response)` | abstract (v16 L126) | ✅ yes |
| `latestUpdatesRequest(page)` | abstract (v16 L149) | ✅ yes |
| `latestUpdatesParse(response)` | abstract (v16 L156) | ✅ yes |
| `searchAnimeRequest(page, query, filters)` | abstract (v16 L135) | ✅ yes |
| `searchAnimeParse(response)` | abstract (v16 L142) | ✅ yes |
| `animeDetailsRequest(anime)` | `open fun` (v16 L175) | ✅ yes |
| `animeDetailsParse(response)` | abstract (v16 L184) | ✅ yes |
| `episodeListRequest(anime)` | `open fun` (v16 L204) | ✅ yes |
| `episodeListParse(response)` | abstract (v16 L213) | ✅ yes |
| `videoListRequest(episode)` | **NONE in v16** — v16 has `videoListRequest(hoster: Hoster)` (v16 L299) | ❌ **WON'T COMPILE: "overrides nothing"** |
| `videoListParse(response)` | **NONE in v16** — v16 has `videoListParse(response, hoster: Hoster)` (v16 L311) | ❌ **WON'T COMPILE: "overrides nothing"** |

### MIGRATION VERDICT (verified)
- **MUST DELETE for v16** (2 lines, compile errors otherwise):
  - `override fun videoListRequest(episode: SEpisode) = throw UnsupportedOperationException()` (L52)
  - `override fun videoListParse(response: Response) = throw UnsupportedOperationException()` (L53)
- **CAN STAY for v16** (10 lines): the `popular*`, `latest*`, `search*`, `animeDetails*`, `episodeList*` overrides still compile. They're functional guardrails forcing subclasses to override the suspend `get*` methods directly (the keiyoushi pattern — see `Jellyfin.kt:128,157,171`).
- The `// TODO: Remove with ext lib 16` comment is misleading — v16 did NOT remove the legacy methods (only changed `videoListRequest`/`videoListParse` signatures to take `Hoster`).

### Adoption in yuzono
Only 3 source files import `keiyoushi.utils.Source`:
- `src/all/jellyfin/.../Jellyfin.kt` (`class Jellyfin(suffix) : Source(), UnmeteredSource`)
- `src/all/stremio/.../Stremio.kt` (`class Stremio : Source()`)
- `src/all/stremio/.../addon/AddonManager.kt`

The majority of yuzono extensions extend `ParsedAnimeHttpSource()` directly (ext-lib 14 pattern). Our ext-lib 16 extensions should prefer the `Source` base class (after deleting the 2 broken overrides) OR extend `AnimeHttpSource` directly.

---

## 8. `UrlUtils.kt` — URL fixing (1 object, 2 functions)

```kotlin
object UrlUtils {
    fun fixUrl(url: String): String?                                    // L9-16
    fun fixUrl(url: String, baseUrl: String): String?                   // L18-42
}
```

### `fixUrl(url)` rules
| Input | Output |
|---|---|
| empty | `null` |
| starts with `"http"` | passthrough |
| starts with `"{\""` (JSON object) | passthrough (guards against treating JSON as URL) |
| starts with `"//"` | `"https:$url"` (protocol-relative → HTTPS) |
| else | strip everything before first `http://`/`https://` (scrapes URLs out of inline scripts) |

### `fixUrl(url, baseUrl)` rules
| Input | Output |
|---|---|
| `baseUrl.toHttpUrlOrNull() == null` | `null` |
| empty `url` | `null` |
| starts with `"http"` / `"{\""` | passthrough |
| starts with `"//"` | `"https:$url"` |
| starts with `"/"` (absolute path) | `"<scheme>://<host>" + url` (base's scheme/host, no path) |
| else (relative path) | `"<scheme>://<host>/<base path minus last segment>/<url>"` |

**Gotchas:**
- Returns `String?` — always null-check or `?:`.
- Does NOT handle `../` path-traversal (just concatenates). For proper resolution, use `baseUrl.toHttpUrl().resolve(url)`.

**Example (`CloseloadExtractor.kt:50`):**
```kotlin
val url = script.getProperty("url:").let { UrlUtils.fixUrl(it, hostUrl) } ?: return
```

---

## 9. `Crypto.kt` — hex + RC4 (5 exports)

| Name | Signature | Purpose |
|---|---|---|
| `String.decodeHex` | `fun String.decodeHex(): ByteArray` (L12) | Hex string → bytes (even length, valid hex; throws `IllegalArgumentException` otherwise) |
| `String.decodeHexToString` | `fun String.decodeHexToString(): String` (L24) | `decodeHex().toString(UTF_8)` |
| `ByteArray.toHex` | `fun ByteArray.toHex(): String` (L26) | Bytes → **lowercase** hex (`%02x`) |
| `String.toHex` | `fun String.toHex(): String` (L28) | `toByteArray().toHex()` (UTF-8) |
| `rc4` | `fun rc4(key: ByteArray, data: ByteArray, skip: Int = 0): ByteArray` (L50-81) | RC4 stream cipher (symmetric). KSA + PRGA. `skip` discards leading keystream bytes (RC4-drop[n] variant). |

**Gotchas:**
- `toHex` produces **lowercase**. `decodeHex` accepts both `a-f` and `A-F`.
- `rc4` arg order is `(key, data)` — easy to get wrong.
- No HMAC/AES/RSA here — those are in `lib/cryptoaes` (CryptoJS-compat AES) and `lib/jewssl` etc.

---

## 10. `Date.kt` — `SimpleDateFormat` helper (1 export)

```kotlin
fun SimpleDateFormat.tryParse(date: String?): Long    // L6-10
```
Null-safe parse. Returns epoch millis on success, `0L` on null/unparseable (`0L` is the Aniyomi convention for "unknown date"). NOT thread-safe (`SimpleDateFormat` isn't).

**Example (`OtakuFR.kt:25`):**
```kotlin
val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
episode.date_upload = fmt.tryParse(element.text())
```

---

## 11. `Context.kt` — Application accessor (1 export)

```kotlin
val applicationContext: Application get() = Injekt.get()   // L7
```
Top-level val — Injekt-injected `Application`. Used by `getPreferences(sourceId)` and anywhere outside a `Source` that needs app context. Don't use from a lib-multisrc module's top-level init (same warning as `delegate`).

---

## 12. `NextJs.kt` + `reactFlight/` — React Flight (RSC) parser (9 exports, 471 lines)

Extracts typed data from Next.js-hydrated pages. Supports App Router (RSC flight data via `self.__next_f.push([id, "<chunk>"])`) and Pages Router (`<script id="__NEXT_DATA__">{...}</script>`).

| Name | Signature (short) | Purpose |
|---|---|---|
| `Document.extractNextJs<T>` (explicit deser) | `fun <T> Document.extractNextJs(predicate: (JsonElement) -> Boolean, deserializer: DeserializationStrategy<T>): T?` (L333) | Walk flight payloads, return first match |
| `Document.extractNextJs<T>` (reified + predicate) | `inline fun <reified T> Document.extractNextJs(noinline predicate): T?` (L355) | Reified overload |
| `Document.extractNextJs<T>` (predicate-free) | `inline fun <reified T> Document.extractNextJs(): T?` (L366) | Auto-infers predicate from `T`'s required fields |
| `String.extractNextJsRsc<T>` (3 overloads) | `... String.extractNextJsRsc(...)` (L388, L408, L419) | Parse raw RSC flight body (for `text/x-component` responses) |
| `Response.extractNextJs<T>` (3 overloads) | `... Response.extractNextJs(...)` (L441, L459, L470) | Auto-dispatches by Content-Type: `text/x-component`→RSC, `text/html`→Document. Throws for other types. |

**`reactFlight/`** (3 typealiases + serializers): `ReactFlightNumber` (Double; `$Infinity`/`$NaN`/`$-0`), `ReactFlightBigInt` (BigInteger; `$n<digits>`), `ReactFlightDate` (Date; `$D<iso>`). Deserialize-only (serialize throws `SerializationException("Stub !")`).

**When to use:** extensions scraping Next.js sites where data is in the RSC flight payload (e.g. modern streaming sites using Next.js App Router).

**Gotchas:**
- `Response.extractNextJs` throws `IllegalStateException` for non-HTML/non-RSC content types — wrap in `runCatching` if server is unpredictable.
- Predicate-free overloads REQUIRE at least one non-optional, non-nullable field in `T`. If all-nullable, use the predicate overload.
- I did NOT grep for actual consumers in yuzono `src/` — likely `miruro` or modern React-based sites, but unverified.

---

## 13. `Collections.kt` — type-filtered find (2 exports)

| Name | Signature | Purpose |
|---|---|---|
| `firstInstance<T>` | `inline fun <reified T> Iterable<*>.firstInstance(): T` (L10) | First element that's an instance of `T`; throws `NoSuchElementException` if none |
| `firstInstanceOrNull<T>` | `inline fun <reified T> Iterable<*>.firstInstanceOrNull(): T?` (L15) | Same but nullable |

More efficient than `filterIsInstance<T>().first()` (single pass, no intermediate list).

---

## 14. Real usage patterns (yuzono `src/`)

### Pattern A — `Source()` subclass with direct suspend `get*` overrides (`Jellyfin.kt`)
```kotlin
class Jellyfin(private val suffix: String) : Source(), UnmeteredSource {
    override val migration: SharedPreferences.() -> Unit = { /* quality migration */ }
    override val json: Json by lazy { Json { ignoreUnknownKeys = true; ... } }
    override var baseUrl by LazyMutable { preferences.hostUrl }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val items = client.get(url).parseAs<ItemListDto>(json)
        // ...
    }
    // ... getLatestUpdates, getSearchAnime, ...
}
```

### Pattern B — preferences + JSON + parallel extraction (`Animeler.kt`)
```kotlin
private val preferences by getPreferencesLazy()
val results: SearchResponseDto = response.parseAs()
val body = data.toJsonRequestBody()
val filteredSources = preferences.getStringSet(PREF_HOSTS_SELECTION_KEY, SUPPORTED_PLAYERS)!!
return filteredSources.parallelCatchingFlatMapBlocking {
    val res = client.newCall(POST(actionUrl, headers, playerBody(it.key))).awaitSuccess().parseAs<VideoDto>()
    videosFromUrl(res.videoSrc)
}
```

### Pattern C — `UrlUtils.fixUrl` + `useAsJsoup` (`CloseloadExtractor.kt`)
```kotlin
val doc = client.newCall(GET(url, headers)).awaitSuccess().useAsJsoup()
val fixedUrl = UrlUtils.fixUrl(rawUrl, hostUrl) ?: return
```

---

## 15. Summary — all `keiyoushi.utils` public API at a glance

| File | Exports | Key |
|---|---|---|
| `Json.kt` | 12 | `jsonInstance`, `parseAs<T>` (×4), `toJsonString`, `toJsonBody`, `toJsonRequestBody`, `toJsonElement` |
| `Protobuf.kt` | 9 | `protoInstance`, `decodeProto<T>`, `encodeProto`, `parseAsProto<T>` (×3), `toRequestBodyProto`, `decodeProtoBase64<T>`, `encodeProtoBase64` |
| `GraphQL.kt` | 12 | `graphQLBody` (×2), `appendGraphQLParams` (×2), `graphQLPost` (×2), `graphQLGet` (×2), `persistedQueryExtension`, `parseGraphQLAs<T>` (×2), `GraphQLErrorInterceptor`, `GraphQLException` |
| `Network.kt` | 7 | `OkHttpClient.get` (×2), `OkHttpClient.post`, `Response.useAsJsoup`, `Response.bodyString`, `commonEmptyHeaders` (deprecated), `commonEmptyRequestBody` (deprecated) |
| `Coroutines.kt` | 11 | `parallelMap*` (×4), `parallelFlatMap*` (×2), `parallelCatchingFlatMap*` (×2), `parallelCatchingMapNotNull`, `catchingFlatMap*` (×2), `flatMapCatching` |
| `Preferences.kt` | 13 | `getPreferences`, `getPreferencesLazy`, `getPreferences(sourceId)`, `LazyMutable<T>`, `PreferenceDelegate<T>`, `SharedPreferences.delegate`, `get/addEditTextPreference`, `get/addListPreference`, `get/addSetPreference`, `get/addSwitchPreference` |
| `Source.kt` | 1 class + 6 members | `Source` abstract class; `context`, `migration`, `json`, `preferences`, `handler`, `displayToast` — **2 legacy overrides MUST be deleted for v16** |
| `UrlUtils.kt` | 1 object + 2 funs | `UrlUtils.fixUrl(url)`, `UrlUtils.fixUrl(url, baseUrl)` |
| `Crypto.kt` | 5 | `decodeHex`, `decodeHexToString`, `toHex` (×2), `rc4` |
| `Date.kt` | 1 | `SimpleDateFormat.tryParse` |
| `Context.kt` | 1 | `applicationContext` |
| `NextJs.kt` + `reactFlight/` | 9 + 3 typealiases | `Document/String/Response.extractNextJs<T>` (×3 each), `ReactFlightNumber/BigInt/Date` |
| `Collections.kt` | 2 | `firstInstance<T>`, `firstInstanceOrNull<T>` |

**Total: 16 Kotlin source files, ~86 public top-level declarations + 1 abstract class (`Source`).**

## 16. Things I could NOT fully verify (honest notes)

1. **`// TODO: Remove with ext lib 16` in `Network.kt` (L18) and `Source.kt` (L37):** the underlying `GET/POST/awaitSuccess/asJsoup` still exist in v16 stubs, so `Network.kt`'s helpers COULD be removed but there's no documented plan. The `Source.kt` legacy overrides: 10 of 12 still compile in v16 (only `videoListRequest(episode)`/`videoListParse(response)` break).
2. **Concurrency limit in `Coroutines.kt`:** none explicit. OkHttp defaults (64/5) throttle HTTP-bound work. No comment explaining the choice. Footgun for non-HTTP or many-host work.
3. **`PreferenceDelegate` "wrong source id" warning for lib-multisrc:** I read the warning but couldn't reproduce a failure scenario. Safe to use inside concrete `src/<lang>/<name>/` extensions (which have the real `id`).
4. **`NextJs.kt` actual adoption:** I did NOT grep for consumers. Likely `miruro` or modern React sites, but unverified.
5. **`ParsedAnimeHttpSource` deprecation interplay with `Source`:** `Source` extends `AnimeHttpSource()` directly (sidesteps the deprecation). Existing yuzono extensions extending `ParsedAnimeHttpSource()` will compile on v16 with deprecation warnings. Migrating them to `Source`/`AnimeHttpSource` is a separate concern.

## 17. Related docs

- `MEMORY/research/02-reference-extension-build-and-structure.md` §4 — the `core/` module overview.
- `MEMORY/research/04-network-layer-and-interceptors.md` — the network layer these utils wrap.
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — the ext-lib 16 API (where `AnimeHttpSource`/`ConfigurableAnimeSource` live).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — the `Source.kt` legacy-override issue is an instance of this.