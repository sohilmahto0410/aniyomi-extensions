package eu.kanade.tachiyomi.animeextension.en.nineanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

// ─── Helpers ──────────────────────────────────────────────────────────────────

open class QueryPartFilter(
    displayName: String,
    val vals: Array<Pair<String, String>>,
) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toQueryPart(): String = vals[state].second
}

private class CheckBox(name: String, val value: String, state: Boolean = false) :
    AnimeFilter.CheckBox(name, state)

open class CheckBoxFilterList(name: String, private val boxes: List<CheckBox>) :
    AnimeFilter.Group<AnimeFilter.CheckBox>(name, boxes) {
    fun checked(): List<String> = boxes.filter { it.state }.map { it.value }
}

// ─── Option arrays ────────────────────────────────────────────────────────────

private val SORT_OPTS = arrayOf(
    Pair("Default", "default"),
    Pair("Recently Updated", "recently_updated"),
    Pair("Recently Added", "recently_added"),
    Pair("Name A-Z", "title_az"),
    Pair("Most Watched", "most_watched"),
    Pair("Score", "scores"),
    Pair("Released Date", "release_date"),
)

private val TYPE_OPTS = arrayOf(
    Pair("All", ""),
    Pair("Movie", "movie"),
    Pair("TV Series", "tv"),
    Pair("OVA", "ova"),
    Pair("ONA", "ona"),
    Pair("Special", "special"),
    Pair("Music", "music"),
)

private val STATUS_OPTS = arrayOf(
    Pair("All", ""),
    Pair("Finished Airing", "finished_airing"),
    Pair("Currently Airing", "currently_airing"),
    Pair("Not yet aired", "not_yet_aired"),
)

private val LANGUAGE_OPTS = arrayOf(
    Pair("All", ""),
    Pair("Subbed", "sub"),
    Pair("Dubbed", "dub"),
)

private val SEASON_OPTS = arrayOf(
    Pair("All", ""),
    Pair("Spring", "spring"),
    Pair("Summer", "summer"),
    Pair("Fall", "fall"),
    Pair("Winter", "winter"),
)

// Genre slugs taken directly from the nav links on 9animetv.to
private val GENRES = listOf(
    Pair("Action", "action"),
    Pair("Adventure", "adventure"),
    Pair("Cars", "cars"),
    Pair("Comedy", "comedy"),
    Pair("Dementia", "dementia"),
    Pair("Demons", "demons"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Game", "game"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Kids", "kids"),
    Pair("Magic", "magic"),
    Pair("Martial Arts", "marial-arts"),   // note: site typo
    Pair("Mecha", "mecha"),
    Pair("Military", "military"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Parody", "parody"),
    Pair("Police", "police"),
    Pair("Psychological", "psychological"),
    Pair("Romance", "romance"),
    Pair("Samurai", "samurai"),
    Pair("School", "school"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shounen", "shounen"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Space", "space"),
    Pair("Sports", "sports"),
    Pair("Super Power", "super-power"),
    Pair("Supernatural", "supernatural"),
    Pair("Thriller", "thriller"),
    Pair("Vampire", "vampire"),
)

// ─── Filter classes ───────────────────────────────────────────────────────────

class SortFilter : QueryPartFilter("Sort", SORT_OPTS)
class TypeFilter : QueryPartFilter("Type", TYPE_OPTS)
class StatusFilter : QueryPartFilter("Status", STATUS_OPTS)
class LanguageFilter : QueryPartFilter("Language", LANGUAGE_OPTS)
class SeasonFilter : QueryPartFilter("Season", SEASON_OPTS)

class YearFilter : AnimeFilter.Text("Year", "")

class GenreFilter : CheckBoxFilterList(
    "Genres",
    GENRES.map { CheckBox(it.first, it.second) },
)

// ─── Full filter list ─────────────────────────────────────────────────────────

fun getFilterList() = listOf(
    SortFilter(),
    TypeFilter(),
    StatusFilter(),
    LanguageFilter(),
    SeasonFilter(),
    YearFilter(),
    GenreFilter(),
)
