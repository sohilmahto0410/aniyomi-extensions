# 9anime Extension for Aniyomi

Targets **[9animetv.to](https://9animetv.to)** — the current official domain of 9anime.

> ⚠️ The old domain `9anime.to` is no longer the official site. The correct URL is `9animetv.to`.

---

## Confirmed site structure (verified March 2026)

| Page | URL pattern |
|---|---|
| Home | `https://9animetv.to/home` |
| Most popular | `/most-popular?page=N` |
| Recently updated | `/recently-updated?page=N` |
| Recently added | `/recently-added?page=N` |
| Search | `/search?keyword=<q>&page=N[&type=…&status=…]` |
| Anime detail | `/watch/<slug>-<numericId>` |
| Episode list API | `/ajax/v2/episode/list/<animeId>` |
| Server list API | `/ajax/v2/episode/servers?episodeId=<epId>` |
| Source URL API | `/ajax/v2/episode/sources?id=<serverId>` |

---

## File structure

```
src/en/nineanime/
├── build.gradle
├── AndroidManifest.xml
└── src/eu/kanade/tachiyomi/animeextension/en/nineanime/
    ├── NineAnime.kt             ← Main source
    ├── NineAnimeDto.kt          ← JSON response models
    ├── NineAnimeFilters.kt      ← Search filters
    └── NineAnimeUrlActivity.kt  ← Deep-link handler
```

---

## How to build

1. Clone a fork of `aniyomiorg/aniyomi-extensions` (e.g. `yuzono/aniyomi-extensions`)
2. Drop this `nineanime/` folder into `src/en/`
3. Build: `./gradlew :src:en:nineanime:assembleRelease`

---

## Preferences

| Setting | Default | Options |
|---|---|---|
| Preferred server | Vidstream | Vidstream, MyCloud, Streamtape, Filemoon |
| Preferred language | Sub | Sub, Dub, Soft Sub |

---

## Common breakage causes

| Cause | Fix |
|---|---|
| Site changed AJAX endpoint paths | Update `/ajax/v2/…` paths in `NineAnime.kt` |
| Listing page HTML restructured | Update selectors in `popularAnimeSelector()` / `popularAnimeFromElement()` |
| Embed JavaScript obfuscation changed | Update regex in `extractHlsFromEmbed()` |

---

## License

Apache License 2.0
