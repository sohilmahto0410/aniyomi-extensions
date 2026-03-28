package eu.kanade.tachiyomi.animeextension.en.nineanime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Episode list ─────────────────────────────────────────────────────────────
// GET /ajax/v2/episode/list/<animeId>
// Returns JSON: { "status": true, "html": "<ul>…</ul>", "totalItems": N }

@Serializable
data class EpisodeListResponse(
    @SerialName("html") val html: String = "",
    @SerialName("totalItems") val totalItems: Int = 0,
)

// ─── Server list ──────────────────────────────────────────────────────────────
// GET /ajax/v2/episode/servers?episodeId=<id>
// Returns JSON: { "status": true, "html": "<div>…</div>" }

@Serializable
data class ServerListResponse(
    @SerialName("html") val html: String = "",
)

// ─── Episode sources ──────────────────────────────────────────────────────────
// GET /ajax/v2/episode/sources?id=<serverId>
// Returns JSON: { "type": "iframe", "link": "https://…", "tracks": [], "intro": {}, "outro": {} }

@Serializable
data class EpisodeSourceResponse(
    @SerialName("type") val type: String = "",
    @SerialName("link") val link: String = "",
)

// ─── Search autocomplete ──────────────────────────────────────────────────────
// GET /ajax/search/suggest?keyword=<q>
// Returns JSON: { "html": "<div>…</div>" }

@Serializable
data class SearchSuggestResponse(
    @SerialName("html") val html: String = "",
)
