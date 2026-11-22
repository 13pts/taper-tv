# Taper TV

A Google TV / Android TV app for the Internet Archive's [Live Music Archive](https://archive.org/details/etree):
every band, every show, every taper. It also covers the [Tapers Section](https://archive.org/details/taperssection), where artists without their own Live Music Archive collection live. Remote-in-hand, no phone needed.

![TV banner](docs/banner-preview.png)

## What it does (v1)

- **Artists → year → show → source.** Pick a band, a year, a night, then which tape of that night.
- **Sort sources** by rating, by taper, soundboards first, or most played.
- **Tapers.** Search a taper by name, follow them, and browse everything they've recorded,
  newest first or by band. Every recording links to "More from <taper>".
- **Search** by band, venue, city or date.
- **On This Day** and **Favorites** (bands, tapers, shows), stored on the TV.
- **Background playback**: the show keeps playing when the screen idles.
- **Self-updating**: Home shows an "Update ready" button whenever a newer build is published.

No account, no backend. The app talks straight to archive.org's public search and metadata APIs.

## Install on the TV

Every push to `main` builds an APK and publishes it as the rolling `latest` release.
Every build is signed with the same key, so new builds install over old ones.

1. In the Downloader app on the TV, enter
   `https://github.com/13pts/taper-tv/releases/latest/download/taper-tv.apk`
   (or a short code for it from [go.aftvnews.com](https://go.aftvnews.com/)).
2. Install, and allow installs from Downloader when asked.
3. After that the app updates itself from the Home screen.

## Build

```
./gradlew assembleDebug
```

Kotlin, Jetpack Compose, Media3. Networking is `HttpURLConnection` + `org.json` on purpose: no extra dependencies.

## Where things live

| Path | What |
| --- | --- |
| `app/src/main/java/.../data/Archive.kt` | archive.org client and data models |
| `app/src/main/java/.../data/Favorites.kt` | on-device favorites |
| `app/src/main/java/.../playback/` | Media3 service and the UI's handle on it |
| `app/src/main/java/.../ui/` | screens, navigation, shared components |
| `brand/` | TaperTV cassette logo: the original (black body) and the yellow-body version the app uses on dark |
| `docs/tighten-up.md` | shortcuts taken to ship v1, to do properly later |

— Vega
