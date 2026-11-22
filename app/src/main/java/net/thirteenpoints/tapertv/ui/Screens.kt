package net.thirteenpoints.tapertv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.thirteenpoints.tapertv.R
import net.thirteenpoints.tapertv.data.Archive
import net.thirteenpoints.tapertv.data.Artist
import net.thirteenpoints.tapertv.data.Favorites
import net.thirteenpoints.tapertv.data.Recording
import net.thirteenpoints.tapertv.data.Show
import net.thirteenpoints.tapertv.data.Updater
import kotlinx.coroutines.launch
import net.thirteenpoints.tapertv.playback.PlayerConnection

private val gap = Arrangement.spacedBy(10.dp)

// ---- Home ------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Nav) {
    val tiles = listOf(
        Triple("Artists", "Every band in the archive", Screen.Artists),
        Triple("Tapers", "Follow the people behind the mics", Screen.Tapers),
        Triple("Search", "Band, venue, city or date", Screen.Search),
        Triple("On This Day", "Shows played on today's date", Screen.OnThisDay),
        Triple("Favorites", "Your bands, tapers and shows", Screen.Favorites),
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Updater.check(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Label("TaperTV", size = 30, bold = true)
                Label("The Internet Archive's Live Music Archive · build ${Updater.currentBuild(context)}",
                    size = 14, color = Palette.Dim)
            }
        }
        Updater.available?.let { update ->
            Row(Modifier.padding(bottom = 14.dp)) {
                Chip(
                    Updater.status.ifBlank { "Update ready: build ${update.build} · install now" },
                    onClick = { if (!Updater.busy) scope.launch { Updater.downloadAndInstall(context, update) } },
                    selected = true,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItemsIndexed(tiles) { i, tile ->
                FocusCard(
                    onClick = { nav.go(tile.third) },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    requestFocus = i == 0,
                ) { focused ->
                    Column {
                        Label(tile.first, size = 22, bold = true, color = if (focused) Palette.Ink else Palette.Text)
                        Label(tile.second, size = 13, maxLines = 2, color = if (focused) Palette.Ink else Palette.Dim)
                    }
                }
            }
        }
    }
}

// ---- Artists ---------------------------------------------------------------------------

@Composable
fun ArtistGrid(artists: List<Artist>, nav: Nav, focusFirst: Boolean) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = rememberLazyGridState(),
        horizontalArrangement = gap,
        verticalArrangement = gap,
    ) {
        gridItemsIndexed(artists) { i, a ->
            FocusCard(
                onClick = { nav.go(Screen.ArtistYears(a)) },
                modifier = Modifier.fillMaxWidth(),
                requestFocus = focusFirst && i == 0,
            ) { focused ->
                Column {
                    Label(a.name, size = 16, bold = true, color = if (focused) Palette.Ink else Palette.Text)
                    Label(
                        when {
                            a.id.startsWith(Archive.CREATOR_PREFIX) -> "${a.count} community tapes"
                            a.count > 0 -> "${a.count} recordings"
                            else -> "Live recordings"
                        },
                        size = 12, color = if (focused) Palette.Ink else Palette.Dim,
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistsScreen(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    val query = debounced(text)
    val load = rememberLoad(query) {
        if (query.isBlank()) Archive.topArtists() else Archive.searchArtists(query)
    }
    Column {
        ScreenTitle("Artists", if (query.isBlank()) "Most-played bands first" else "Bands matching \"$query\"")
        SearchField(text, { text = it }, "Find a band or artist")
        LoadBox(load) { list ->
            if (list.isEmpty()) Label("No bands found.", color = Palette.Dim)
            else ArtistGrid(list, nav, focusFirst = query.isBlank())
        }
    }
}

@Composable
fun ArtistYearsScreen(artist: Artist, nav: Nav) {
    val load = rememberLoad(artist.id) { Archive.artistYears(artist.id) }
    val fav = Favorites.hasArtist(artist.id)
    Column {
        ScreenTitle(artist.name, "Pick a year")
        Row(Modifier.padding(bottom = 12.dp)) {
            Chip(if (fav) "★ Favorite band" else "☆ Add to favorites", onClick = { Favorites.toggle(artist) }, selected = fav)
        }
        LoadBox(load) { years ->
            if (years.isEmpty()) Label("No dated recordings for this band.", color = Palette.Dim)
            else LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                state = rememberLazyGridState(),
                horizontalArrangement = gap,
                verticalArrangement = gap,
            ) {
                gridItemsIndexed(years) { i, y ->
                    FocusCard(
                        onClick = { nav.go(Screen.Year(artist, y)) },
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = i == 0,
                    ) { focused ->
                        Label(y.toString(), size = 17, bold = true, color = if (focused) Palette.Ink else Palette.Text)
                    }
                }
            }
        }
    }
}

@Composable
fun YearScreen(artist: Artist, year: Int, nav: Nav) {
    val load = rememberLoad(artist.id, year) { Archive.showsForYear(artist.id, year) }
    Column {
        ScreenTitle("${artist.name} · $year", "Pick a show")
        LoadBox(load) { shows ->
            if (shows.isEmpty()) Label("Nothing taped that year.", color = Palette.Dim)
            else LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                itemsIndexed(shows) { i, show ->
                    val n = show.sources.size
                    RowItem(
                        title = show.date.ifBlank { "Undated" },
                        subtitle = show.place,
                        trailing = if (n == 1) "1 version" else "$n versions",
                        onClick = { nav.go(Screen.RecordingPage(bestOf(show.sources), show.sources)) },
                        requestFocus = i == 0,
                    )
                }
            }
        }
    }
}

// ---- A show and its sources ------------------------------------------------------------

private enum class SourceSort(val label: String) {
    Rating("Top rated"), Taper("By taper"), Soundboard("Soundboards first"), Plays("Most played")
}

@Composable
fun ShowSourcesScreen(artistName: String, show: Show, nav: Nav) {
    var sortName by rememberSaveable { mutableStateOf(SourceSort.Rating.name) }
    val sort = SourceSort.valueOf(sortName)
    val sources = remember(show, sort) {
        when (sort) {
            SourceSort.Rating -> show.sources.sortedWith(
                compareByDescending<Recording> { it.rating }.thenByDescending { it.downloads })
            SourceSort.Taper -> show.sources.sortedWith(
                compareBy<Recording> { it.taper.isBlank() }.thenBy { it.taper.lowercase() })
            SourceSort.Soundboard -> show.sources.sortedWith(
                compareByDescending<Recording> { it.isSoundboard }.thenByDescending { it.rating })
            SourceSort.Plays -> show.sources.sortedByDescending { it.downloads }
        }
    }
    Column {
        ScreenTitle("$artistName · ${show.date}", listOf(show.place, "Pick a version").filter { it.isNotBlank() }.joinToString(" · "))
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = gap) {
            SourceSort.values().forEach { s ->
                Chip(s.label, onClick = { sortName = s.name }, selected = s == sort)
            }
        }
        LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
            itemsIndexed(sources) { i, rec ->
                RowItem(
                    title = rec.taper.ifBlank { "Unknown taper" },
                    subtitle = rec.source.ifBlank { rec.id },
                    trailing = stats(rec),
                    onClick = { nav.go(Screen.RecordingPage(rec, show.sources)) },
                    requestFocus = i == 0,
                )
            }
        }
    }
}

// ---- One recording ---------------------------------------------------------------------

@Composable
fun RecordingScreen(rec: Recording, versions: List<Recording>, nav: Nav, player: PlayerConnection) {
    val load = rememberLoad(rec.id) { Archive.detail(rec.id) }
    val fav = Favorites.hasRecording(rec.id)
    LoadBox(load) { detail ->
        val r = detail.recording
        val playingThis = player.recordingId == r.id
        Row {
            Column(Modifier.weight(0.42f).padding(end = 24.dp), verticalArrangement = gap) {
                Label(r.artist.ifBlank { rec.artist }, size = 24, bold = true, maxLines = 2)
                Label(listOf(r.date, r.place).filter { it.isNotBlank() }.joinToString(" · "), size = 15, maxLines = 2)
                if (stats(rec).isNotBlank()) Label(stats(rec), size = 13, color = Palette.Gold)
                Chip("▶ Play show", onClick = { player.play(detail, 0) }, requestFocus = true)
                if (versions.size > 1) {
                    val isBest = bestOf(versions).id == rec.id
                    Chip(
                        (if (isBest) "Top-rated of " else "One of ") + "${versions.size} versions · switch",
                        onClick = {
                            nav.go(Screen.ShowSources(
                                r.artist.ifBlank { rec.artist },
                                Show(r.date.ifBlank { rec.date }, r.place.ifBlank { rec.place }, versions),
                            ))
                        },
                    )
                }
                Chip(if (fav) "★ Saved show" else "☆ Save show", onClick = { Favorites.toggle(rec.copy(taper = r.taper.ifBlank { rec.taper })) }, selected = fav)
                // A recording can credit several tapers; link each one.
                r.taper.split(",", ";", "&", " and ").map { it.trim() }.filter { it.length > 1 }.distinct().take(3)
                    .forEach { name -> Chip("More from $name", onClick = { nav.go(Screen.Taper(name)) }) }
                if (r.taper.isBlank()) Label("Taper not credited", size = 13, color = Palette.Dim)
                if (r.source.isNotBlank()) Label("Source: ${r.source}", size = 12, color = Palette.Dim, maxLines = 3)
                if (detail.lineage.isNotBlank()) Label("Lineage: ${detail.lineage}", size = 12, color = Palette.Dim, maxLines = 3)
                if (detail.transferer.isNotBlank()) Label("Transfer: ${detail.transferer}", size = 12, color = Palette.Dim, maxLines = 2)
                player.error?.let { Label(it, size = 13, color = Palette.Flame, maxLines = 2) }
            }
            Column(Modifier.weight(0.58f)) {
                if (detail.tracks.isEmpty()) {
                    Label("No streamable tracks on this recording.", color = Palette.Dim)
                } else {
                    LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(detail.tracks) { i, t ->
                            RowItem(
                                title = "${i + 1}. ${t.title}",
                                trailing = if (t.lengthSec > 0) clock(t.lengthSec.toLong()) else "",
                                selected = playingThis && player.trackIndex == i,
                                onClick = { player.play(detail, i) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Tapers ----------------------------------------------------------------------------

@Composable
fun TaperScreen(name: String, nav: Nav) {
    val load = rememberLoad(name) { Archive.byTaper(name) }
    var byArtist by rememberSaveable { mutableStateOf(false) }
    val fav = Favorites.hasTaper(name)
    Column {
        ScreenTitle(name, "Everything this taper has in the archive")
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = gap) {
            Chip(if (fav) "★ Following" else "☆ Follow taper", onClick = { Favorites.toggleTaper(name) }, selected = fav)
            Chip("Newest first", onClick = { byArtist = false }, selected = !byArtist)
            Chip("By band", onClick = { byArtist = true }, selected = byArtist)
        }
        LoadBox(load) { recs ->
            val sorted = remember(recs, byArtist) {
                if (byArtist) recs.sortedWith(compareBy<Recording> { it.artist.lowercase() }.thenBy { it.date })
                else recs.sortedByDescending { it.date }
            }
            if (sorted.isEmpty()) Label("No recordings credited to that name.", color = Palette.Dim)
            else {
                val bands = sorted.map { it.artist }.distinct().size
                val groups = remember(sorted) { groupByShow(sorted) }
                Label("${groups.size} shows · ${sorted.size} recordings · $bands bands", size = 13,
                    color = Palette.Dim, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                    itemsIndexed(groups) { i, group -> ShowGroupRow(group, nav, requestFocus = i == 0) }
                }
            }
        }
    }
}

@Composable
fun TapersScreen(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    val query = debounced(text)
    Column {
        ScreenTitle("Tapers", "Search a taper's name, or pick one you follow")
        SearchField(text, { text = it }, "Taper name")
        if (query.isBlank()) {
            if (Favorites.tapers.isEmpty()) {
                Label("You're not following any tapers yet. Open a recording and hit \"More from…\" to find one.",
                    color = Palette.Dim, maxLines = 3)
            } else {
                LazyColumn(verticalArrangement = gap) {
                    itemsIndexed(Favorites.tapers.toList()) { i, name ->
                        RowItem(name, onClick = { nav.go(Screen.Taper(name)) }, trailing = "Following", requestFocus = i == 0)
                    }
                }
            }
        } else {
            val load = rememberLoad(query) { Archive.findTapers(query) }
            LoadBox(load) { hits ->
                if (hits.isEmpty()) Label("No tapers by that name.", color = Palette.Dim)
                else LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                    itemsIndexed(hits) { _, hit ->
                        RowItem(hit.name, onClick = { nav.go(Screen.Taper(hit.name)) },
                            trailing = "${hit.count} of the top matches")
                    }
                }
            }
        }
    }
}

// ---- Search / On this day / Favorites --------------------------------------------------

@Composable
fun SearchScreen(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    val query = debounced(text)
    Column {
        ScreenTitle("Search", "Try a band, a venue, a city, or a date like 1977-05-08")
        SearchField(text, { text = it }, "Search the Live Music Archive")
        if (query.isNotBlank()) {
            val load = rememberLoad(query) { Archive.searchArtists(query).take(6) to Archive.searchRecordings(query) }
            LoadBox(load) { (artists, recs) ->
                if (artists.isEmpty() && recs.isEmpty()) Label("Nothing matched.", color = Palette.Dim)
                else LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                    itemsIndexed(artists) { _, a ->
                        RowItem(a.name, onClick = { nav.go(Screen.ArtistYears(a)) }, trailing = "Band")
                    }
                    itemsIndexed(groupByShow(recs)) { _, group -> ShowGroupRow(group, nav) }
                }
            }
        }
    }
}

@Composable
fun OnThisDayScreen(nav: Nav) {
    val load = rememberLoad("on-this-day") { Archive.onThisDay() }
    Column {
        ScreenTitle("On This Day", "Shows played on today's date, most-played first")
        LoadBox(load) { recs ->
            if (recs.isEmpty()) Label("Nothing in the archive for today's date.", color = Palette.Dim)
            else LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                itemsIndexed(groupByShow(recs)) { i, group -> ShowGroupRow(group, nav, requestFocus = i == 0) }
            }
        }
    }
}

@Composable
fun FavoritesScreen(nav: Nav) {
    val artists = Favorites.artists.toList()
    val tapers = Favorites.tapers.toList()
    val recs = Favorites.recordings.toList()
    Column {
        ScreenTitle("Favorites")
        if (artists.isEmpty() && tapers.isEmpty() && recs.isEmpty()) {
            Label("Nothing saved yet. Star a band, follow a taper, or save a show.", color = Palette.Dim, maxLines = 2)
        } else {
            LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                itemsIndexed(artists) { i, a ->
                    RowItem(a.name, onClick = { nav.go(Screen.ArtistYears(a)) }, trailing = "Band", requestFocus = i == 0)
                }
                itemsIndexed(tapers) { i, t ->
                    RowItem(t, onClick = { nav.go(Screen.Taper(t)) }, trailing = "Taper",
                        requestFocus = artists.isEmpty() && i == 0)
                }
                itemsIndexed(recs) { i, rec ->
                    RecordingRow(rec, onClick = { nav.go(Screen.RecordingPage(rec)) },
                        requestFocus = artists.isEmpty() && tapers.isEmpty() && i == 0)
                }
            }
        }
    }
}
