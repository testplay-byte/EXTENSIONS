package eu.kanade.tachiyomi.animeextension.en.mkissa

/**
 * GraphQL query strings for the api.allanime.day API (used by mkissa.to's frontend).
 *
 * The API accepts both GET+APQ (persisted queries) and POST+full-query. We use POST with
 * the full query string (matches the proven allanime reference extension pattern — no
 * dependency on capturing/maintaining persisted-query hashes).
 *
 * `%` is used as a placeholder for `$` (substituted by [buildQuery]) so the Kotlin
 * string template doesn't try to interpolate them.
 */
fun buildQuery(queryAction: () -> String): String = queryAction()
    .trimIndent()
    .replace("%", "$")

/**
 * Popular: queryPopular(type, size, dateRange, page) → ranked by views in the date range.
 * dateRange: 1=Daily, 7=Weekly, 30=Monthly (we use 7 = Weekly for "popular").
 */
val POPULAR_QUERY: String = buildQuery {
    """
        query(
            %type: VaildPopularTypeEnumType!
            %size: Int!
            %page: Int
            %dateRange: Int
        ) {
            queryPopular(
                type: %type
                size: %size
                dateRange: %dateRange
                page: %page
            ) {
                total
                recommendations {
                    anyCard {
                        _id
                        name
                        thumbnail
                        englishName
                        nativeName
                        slugTime
                    }
                }
            }
        }
    """
}

/**
 * Search/Latest: shows(search, limit, page, translationType, countryOrigin) → paginated list.
 * - For Latest: search = { sortBy: "Recent" } (no query string).
 * - For Search: search = { query: <q>, ...filters }.
 */
val SEARCH_QUERY: String = buildQuery {
    """
        query(
            %search: SearchInput
            %limit: Int
            %page: Int
            %translationType: VaildTranslationTypeEnumType
            %countryOrigin: VaildCountryOriginEnumType
        ) {
            shows(
                search: %search
                limit: %limit
                page: %page
                translationType: %translationType
                countryOrigin: %countryOrigin
            ) {
                pageInfo {
                    total
                }
                edges {
                    _id
                    name
                    thumbnail
                    englishName
                    nativeName
                    slugTime
                }
            }
        }
    """
}

/** Details: show(_id) → full anime metadata (description, genres, status, studios, etc.).
 *
 * ★ CRITICAL: `season`, `airedStart`, `availableEpisodes` are scalar Object types in the
 * GraphQL schema — they MUST be queried WITHOUT subselections (no `{ quarter year }`).
 * Querying them with subselections causes GRAPHQL_VALIDATION_FAILED. The server returns
 * the full nested JSON object as a scalar, which kotlinx.serialization decodes into the
 * DTO's nested data classes. (Verified session 02 — this was the details-page bug.)
 */
val DETAILS_QUERY: String = buildQuery {
    """
        query(%_id: String!) {
            show(_id: %_id) {
                _id
                name
                englishName
                nativeName
                thumbnail
                banner
                description
                type
                season
                score
                averageScore
                rating
                genres
                status
                studios
                airedStart
                availableEpisodes
                episodeDuration
                episodeCount
            }
        }
    """
}

/** Episodes: show(_id) → availableEpisodesDetail { sub: [...], dub: [...] } (episode strings). */
val EPISODES_QUERY: String = buildQuery {
    """
        query(%_id: String!) {
            show(_id: %_id) {
                _id
                availableEpisodesDetail
            }
        }
    """
}
