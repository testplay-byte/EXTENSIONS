package eu.kanade.tachiyomi.animeextension.en.reanime.extractor

/**
 * Minimal HLS m3u8 parser — extracts quality variants from a master playlist.
 *
 * Used to parse the master playlist returned by flixcloud.cc's `/api/m3u8/<token>`
 * endpoint. The master contains multiple `#EXT-X-STREAM-INF` entries with
 * RESOLUTION/BANDWIDTH attributes, each followed by a variant playlist URL.
 */
object PlaylistUtils {

    data class Variant(
        val url: String,
        val resolution: String = "",     // e.g. "1920x1080"
        val bandwidth: Int = 0,          // bytes/sec
        val qualityLabel: String = "",   // e.g. "1080p"
    )

    /**
     * Parse a master m3u8 playlist and return all variant streams.
     * If the playlist is a media playlist (not a master), returns a single
     * entry with the source URL.
     */
    fun parseMaster(body: String, baseUrl: String): List<Variant> {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val variants = mutableListOf<Variant>()

        // Check if this is a master playlist (contains EXT-X-STREAM-INF)
        val hasStreamInf = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        if (!hasStreamInf) {
            // It's a media playlist — return as a single variant
            return listOf(Variant(url = baseUrl, qualityLabel = "Default"))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // Parse attributes from the STREAM-INF line
                val resolution = extractAttr(line, "RESOLUTION") ?: ""
                val bandwidth = extractAttr(line, "BANDWIDTH")?.toIntOrNull() ?: 0

                // The next non-comment line is the variant URL
                if (i + 1 < lines.size) {
                    val variantUrl = resolveUrl(lines[i + 1], baseUrl)
                    val qualityLabel = resolutionToLabel(resolution, bandwidth)
                    variants.add(
                        Variant(
                            url = variantUrl,
                            resolution = resolution,
                            bandwidth = bandwidth,
                            qualityLabel = qualityLabel,
                        ),
                    )
                    i += 2
                    continue
                }
            }
            i++
        }

        return variants
    }

    /** Extract an attribute value from an EXT-X-STREAM-INF line. */
    private fun extractAttr(line: String, key: String): String? {
        // Attributes are KEY=VALUE, separated by commas
        // RESOLUTION=1920x1080, BANDWIDTH=8000000
        val regex = Regex("$key=([^,]+)")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }

    /** Resolve a possibly-relative URL against the base URL. */
    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                // Relative to host
                val host = baseUrl.substringBefore("://") + "://" +
                    baseUrl.substringAfter("://").substringBefore("/")
                "$host$url"
            }
            else -> {
                // Relative to path
                val baseDir = baseUrl.substringBeforeLast("/") + "/"
                "$baseDir$url"
            }
        }
    }

    /** Convert a resolution string (e.g. "1920x1080") to a quality label (e.g. "1080p"). */
    private fun resolutionToLabel(resolution: String, bandwidth: Int): String {
        if (resolution.isNotEmpty()) {
            val height = resolution.substringAfter("x").substringBefore("X")
            val h = height.toIntOrNull()
            if (h != null) return "${h}p"
        }
        // Fallback: estimate from bandwidth
        return when {
            bandwidth >= 8_000_000 -> "1080p"
            bandwidth >= 4_000_000 -> "720p"
            bandwidth >= 2_000_000 -> "480p"
            bandwidth >= 800_000 -> "360p"
            else -> "SD"
        }
    }
}
