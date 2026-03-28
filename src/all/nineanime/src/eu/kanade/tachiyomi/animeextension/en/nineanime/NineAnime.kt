package eu.kanade.tachiyomi.animeextension.en.nineanime

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NineAnime :
    ConfigurableAnimeSource,
    ParsedAnimeHttpSource() {

    // ── Source identity ────────────────────────────────────────────────────────
    override val name = "9anime"
    override val baseUrl = "https://9animetv.to"      // ← corrected domain
    override val lang = "en"
    override val supportsLatest = true

    private val TAG = "NineAnime"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ── Headers ────────────────────────────────────────────────────────────────
    // 9animetv.to is a no-auth public site; standard browser headers suffice.
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    // ══════════════════════════════════════════════════════════════════════════
    // POPULAR  →  /most-popular?page=N
    //
    // Confirmed selectors from live HTML:
    //   Anime card:  div.film-list .item  (or .flw-item on some pages)
    //     └─ a.film-name href="/watch/<slug>"
    //     └─ img[data-src] or img[src]
    // ══════════════════════════════════════════════════════════════════════════

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/most-popular?page=$page", headers)

    // The listing pages render cards as:
    //   <div class="flw-item">
    //     <div class="film-poster">
    //       <a href="/watch/attack-on-titan-112">
    //         <img data-src="…thumbnail…" class="film-poster-img" />
    //       </a>
    //     </div>
    //     <div class="film-detail">
    //       <h3 class="film-name"><a href="/watch/…">Attack on Titan</a></h3>
    //     </div>
    //   </div>
    override fun popularAnimeSelector() = "div.flw-item, div.film-list .item"

    override fun popularAnimeNextPageSelector() =
        "li.page-item a[title=Next], ul.pagination a[title=Next]"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val anchor = element.selectFirst("h3.film-name a, .dynamic-name")
            ?: element.selectFirst("a[href^='/watch/']")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text().ifBlank { anchor.attr("title") }
        thumbnail_url = element.selectFirst("img")?.run {
            attr("data-src").ifBlank { attr("src") }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LATEST  →  /recently-updated?page=N
    // ══════════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/recently-updated?page=$page", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    // ══════════════════════════════════════════════════════════════════════════
    // SEARCH  →  /search?keyword=<q>&page=N[&type=…&status=…&…]
    //
    // The site also has /filter for the advanced filter page, but plain
    // /search?keyword= works for text search and most filters.
    // ══════════════════════════════════════════════════════════════════════════

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (filter.toQueryPart() != "default")
                        addQueryParameter("sort", filter.toQueryPart())

                    is TypeFilter -> if (filter.toQueryPart().isNotEmpty())
                        addQueryParameter("type", filter.toQueryPart())

                    is StatusFilter -> if (filter.toQueryPart().isNotEmpty())
                        addQueryParameter("status", filter.toQueryPart())

                    is LanguageFilter -> if (filter.toQueryPart().isNotEmpty())
                        addQueryParameter("language", filter.toQueryPart())

                    is SeasonFilter -> if (filter.toQueryPart().isNotEmpty())
                        addQueryParameter("season", filter.toQueryPart())

                    is YearFilter -> if (filter.state.isNotBlank())
                        addQueryParameter("year", filter.state)

                    is GenreFilter -> filter.checked().forEach { slug ->
                        addQueryParameter("genre[]", slug)
                    }

                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun getFilterList() = AnimeFilterList(getFilterList())

    // ══════════════════════════════════════════════════════════════════════════
    // ANIME DETAILS  →  /watch/<slug>-<id>
    //
    // Confirmed from live HTML of https://9animetv.to/watch/one-piece-100 :
    //
    //   h2.film-name  (or h1.heading-name)
    //   img in .film-poster
    //   Description block (text paragraph after alt names)
    //   Info items: Type / Studios / Date aired / Status / Genre / Scores …
    // ══════════════════════════════════════════════════════════════════════════

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h2.film-name, h1.heading-name")?.text() ?: ""

        thumbnail_url = document.selectFirst(".film-poster img")?.run {
            attr("src").ifBlank { attr("data-src") }
        }

        // Description: The long paragraph below the alternative names
        description = buildString {
            document.selectFirst(".film-description .text, div.synopsis p")?.text()?.let {
                append(it); append("\n\n")
            }
            infoText(document, "Studios")?.let { append("Studios: $it\n") }
            infoText(document, "Date aired")?.let { append("Aired: $it\n") }
            infoText(document, "Duration")?.let { append("Duration: $it\n") }
            infoText(document, "Type")?.let { append("Type: $it\n") }
            infoText(document, "Premiered")?.let { append("Premiered: $it\n") }
        }.trim()

        genre = document.select(".item-list.genre a, .film-stats .item.genres a").joinToString { it.text() }
            .ifBlank {
                // Fallback: find Genre: row in the info block
                document.selectFirst(".film-stats:contains(Genre), div:contains(Genre:)")
                    ?.text()?.substringAfter("Genre:")?.trim()
            }

        status = when (
            infoText(document, "Status")?.lowercase()
                ?: document.selectFirst(".film-stats span:contains(Currently), .item.status a")
                    ?.text()?.lowercase()
        ) {
            "currently airing" -> SAnime.ONGOING
            "finished airing" -> SAnime.COMPLETED
            "not yet aired" -> SAnime.LICENSED
            else -> SAnime.UNKNOWN
        }
    }

    /** Pull the text value from an info row matching [label] in the detail page. */
    private fun infoText(doc: Document, label: String): String? =
        doc.selectFirst(".film-stats .item:contains($label) .name, .anisc-info .item:contains($label) .name, .pre-tags + div span:contains($label)")
            ?.text()

    // ══════════════════════════════════════════════════════════════════════════
    // EPISODE LIST
    //
    // 9animetv.to uses the same AJAX pattern as the Aniwatch family:
    //   1. Detail page has a numeric anime id in  data-id  attribute on a container
    //   2. Fetch  GET /ajax/v2/episode/list/<animeId>
    //   3. Parse returned HTML fragment for episode <a> tags
    // ══════════════════════════════════════════════════════════════════════════

    override fun episodeListRequest(anime: SAnime): Request =
        GET("$baseUrl${anime.url}", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()

        // The anime id is embedded as data-id on the player/watch container.
        // Typical markup: <div id="detail-dp" data-id="100">
        val animeId = document.selectFirst("[data-id]")?.attr("data-id")
            ?: extractIdFromUrl(response.request.url.toString())
            ?: run {
                Log.e(TAG, "Could not extract anime ID from ${response.request.url}")
                return emptyList()
            }

        val episodeHtml = runBlocking {
            withContext(Dispatchers.IO) {
                val res = client.newCall(
                    GET("$baseUrl/ajax/v2/episode/list/$animeId", ajaxHeaders()),
                ).await()
                json.decodeFromString<EpisodeListResponse>(res.body.string()).html
            }
        }

        if (episodeHtml.isBlank()) return emptyList()

        return Jsoup.parse(episodeHtml)
            .select("a.ep-item, ul.ulclear li a")
            .mapIndexed { idx, el ->
                SEpisode.create().apply {
                    val epNum = el.attr("data-number").toFloatOrNull()
                        ?: el.attr("title").filterFirst(Regex("\\d+(\\.\\d+)?"))?.toFloatOrNull()
                        ?: (idx + 1).toFloat()
                    val epId = el.attr("data-id").ifBlank { el.attr("href").substringAfterLast("?ep=") }

                    name = el.attr("title").ifBlank { "Episode $epNum" }
                    episode_number = epNum
                    // Store original watch URL slug + episode id for the video extractor
                    setUrlWithoutDomain("${response.request.url.encodedPath}?ep=$epId")
                    date_upload = 0L
                }
            }
            .reversed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VIDEO LIST
    //
    // Two AJAX calls:
    //   1. GET /ajax/v2/episode/servers?episodeId=<epId>
    //      → HTML fragment listing server <div> elements
    //   2. GET /ajax/v2/episode/sources?id=<serverId>
    //      → JSON with { "link": "https://embed.example/…" }
    // ══════════════════════════════════════════════════════════════════════════

    override fun videoListRequest(episode: SEpisode): Request =
        GET("$baseUrl${episode.url}", headers)

    override fun videoListParse(response: Response): List<Video> {
        val epId = response.request.url.queryParameter("ep")
            ?: run {
                Log.e(TAG, "No episode id in ${response.request.url}")
                return emptyList()
            }

        val serverHtml = runBlocking {
            withContext(Dispatchers.IO) {
                val res = client.newCall(
                    GET("$baseUrl/ajax/v2/episode/servers?episodeId=$epId", ajaxHeaders()),
                ).await()
                json.decodeFromString<ServerListResponse>(res.body.string()).html
            }
        }

        if (serverHtml.isBlank()) return emptyList()

        val prefServer = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT)!!
        val prefType = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT)!!

        data class Server(val id: String, val name: String, val type: String)

        val servers = Jsoup.parse(serverHtml)
            .select("div.server-item, div.item.server-item")
            .map { el ->
                Server(
                    id = el.attr("data-id").ifBlank { el.attr("data-server-id") },
                    name = el.selectFirst("a, span")?.text() ?: el.attr("data-id"),
                    type = el.attr("data-type").ifBlank { el.closest("[data-type]")?.attr("data-type") ?: "sub" },
                )
            }
            .filter { it.id.isNotBlank() }
            .sortedWith(
                compareByDescending<Server> { it.type.equals(prefType, ignoreCase = true) }
                    .thenByDescending { it.name.equals(prefServer, ignoreCase = true) },
            )

        return runBlocking {
            withContext(Dispatchers.IO) {
                servers.map { server ->
                    async {
                        runCatching { fetchVideosForServer(server.id, server.name, server.type) }
                            .getOrElse {
                                Log.e(TAG, "Server ${server.name} failed: $it")
                                emptyList()
                            }
                    }
                }.awaitAll().flatten()
            }
        }
    }

    private suspend fun fetchVideosForServer(
        serverId: String,
        serverName: String,
        type: String,
    ): List<Video> {
        val sourceRes = client.newCall(
            GET("$baseUrl/ajax/v2/episode/sources?id=$serverId", ajaxHeaders()),
        ).await()
        val embedUrl = json.decodeFromString<EpisodeSourceResponse>(sourceRes.body.string()).link

        if (embedUrl.isBlank()) return emptyList()
        Log.d(TAG, "Server=$serverName type=$type embed=$embedUrl")

        return when {
            "vidsrc" in embedUrl || "vidstream" in embedUrl || "mcloud" in embedUrl ->
                extractHlsFromEmbed(embedUrl, serverName, type)
            "streamtape" in embedUrl ->
                extractStreamtape(embedUrl, serverName, type)
            "filemoon" in embedUrl || "moon" in embedUrl ->
                extractHlsFromEmbed(embedUrl, serverName, type)
            else -> {
                Log.w(TAG, "Unknown embed host for $serverName: $embedUrl")
                emptyList()
            }
        }
    }

    // ── HLS embed extractor ────────────────────────────────────────────────────

    private suspend fun extractHlsFromEmbed(
        embedUrl: String,
        serverName: String,
        type: String,
    ): List<Video> = runCatching {
        val res = client.newCall(GET(embedUrl, embedHeaders(embedUrl))).await()
        val body = res.body.string()

        // Many embeds expose the master m3u8 in a `file: "…"` or `src: "…"` JS literal
        val masterUrl = Regex("""["']?(https?://[^"'\s]+\.m3u8[^"'\s]*)["']?""")
            .find(body)?.groupValues?.get(1)
            ?: return@runCatching emptyList()

        parseM3u8(masterUrl, serverName, type, embedUrl)
    }.getOrElse {
        Log.e(TAG, "HLS embed extraction failed for $embedUrl: $it")
        emptyList()
    }

    private fun parseM3u8(
        masterUrl: String,
        serverName: String,
        type: String,
        referer: String,
    ): List<Video> = runCatching {
        val playlistHeaders = Headers.Builder()
            .add("Referer", referer)
            .add("User-Agent", USER_AGENT)
            .build()

        val body = client.newCall(GET(masterUrl, playlistHeaders)).execute().body.string()
        val baseUrl = masterUrl.substringBeforeLast("/")

        if ("#EXT-X-STREAM-INF" in body) {
            body.lines().windowed(2).mapNotNull { (info, urlLine) ->
                if (!info.startsWith("#EXT-X-STREAM-INF")) return@mapNotNull null
                val res = Regex("""RESOLUTION=(\d+x\d+)""").find(info)?.groupValues?.get(1) ?: "?"
                val videoUrl = if (urlLine.startsWith("http")) urlLine else "$baseUrl/$urlLine"
                Video(videoUrl, "$serverName (${type.uppercase()}) $res", videoUrl, playlistHeaders)
            }
        } else {
            listOf(Video(masterUrl, "$serverName (${type.uppercase()})", masterUrl, playlistHeaders))
        }
    }.getOrElse { emptyList() }

    // ── Streamtape extractor ───────────────────────────────────────────────────

    private suspend fun extractStreamtape(
        embedUrl: String,
        serverName: String,
        type: String,
    ): List<Video> = runCatching {
        val body = client.newCall(GET(embedUrl, embedHeaders(embedUrl))).await().body.string()

        // Streamtape hides the URL by concatenating two JS strings
        val match = Regex("""'([^']+)'\s*\+\s*'([^']+)'""").find(body) ?: return@runCatching emptyList()
        val token = match.groupValues[1] + match.groupValues[2].drop(1)
        val videoUrl = "https://streamtape.com/get_video?$token&stream=1"

        listOf(Video(videoUrl, "$serverName (${type.uppercase()})", videoUrl))
    }.getOrElse { emptyList() }

    // ── Misc helpers ───────────────────────────────────────────────────────────

    /** Headers for AJAX calls to the same origin. */
    private fun ajaxHeaders() = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")
        .add("User-Agent", USER_AGENT)
        .build()

    /** Headers for third-party embed URLs. */
    private fun embedHeaders(embedUrl: String) = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)
        .build()

    /**
     * 9animetv.to URLs look like /watch/one-piece-100.
     * The numeric id is everything after the last '-'.
     */
    private fun extractIdFromUrl(url: String): String? =
        Regex("""/watch/[a-z0-9-]+-(\d+)""").find(url)?.groupValues?.get(1)

    private fun String.filterFirst(regex: Regex): String? = regex.find(this)?.value

    // ══════════════════════════════════════════════════════════════════════════
    // Abstract method stubs — we override the full *Parse functions above
    // ══════════════════════════════════════════════════════════════════════════

    override fun episodeListSelector(): String = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ══════════════════════════════════════════════════════════════════════════
    // PREFERENCES
    // ══════════════════════════════════════════════════════════════════════════

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SERVER_KEY
            title = "Preferred server"
            entries = SERVER_NAMES
            entryValues = SERVER_NAMES
            setDefaultValue(PREF_SERVER_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_TYPE_KEY
            title = "Preferred language"
            entries = arrayOf("Sub", "Dub", "Soft Sub")
            entryValues = arrayOf("sub", "dub", "softsub")
            setDefaultValue(PREF_TYPE_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private const val PREF_SERVER_KEY = "preferred_server"
        private const val PREF_SERVER_DEFAULT = "Vidstream"
        private val SERVER_NAMES = arrayOf("Vidstream", "MyCloud", "Streamtape", "Filemoon")

        private const val PREF_TYPE_KEY = "preferred_type"
        private const val PREF_TYPE_DEFAULT = "sub"
    }
}
