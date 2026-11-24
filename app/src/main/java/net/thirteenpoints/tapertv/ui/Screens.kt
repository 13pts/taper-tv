package net.thirteenpoints.tapertv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
import net.thirteenpoints.tapertv.data.History
import net.thirteenpoints.tapertv.data.Recording
import net.thirteenpoints.tapertv.data.Show
import net.thirteenpoints.tapertv.data.TileExport
import net.thirteenpoints.tapertv.data.Updater
import kotlinx.coroutines.launch
import net.thirteenpoints.tapertv.playback.PlayerConnection

private val gap = Arrangement.spacedBy(10.dp)

// ---- Home ------------------------------------------------------------------------------

private val homeTabs = listOf("Artist", "Taper", "Date", "On this day")

private fun <T> LazyListScope.loaded(load: Load<T>?, content: LazyListScope.(T) -> Unit) {
    when (load) {
        null, is Load.Loading -> item { Label("Loading…", color = Palette.Dim) }
        is Load.Err -> item { Label(load.message, color = Palette.Flame) }
        is Load.Ok -> content(load.value)
    }
}

/** One card in the Home favorites strip: a band, a taper or a saved show. */
@Composable
private fun FavCard(title: String, kind: String, requestFocus: Boolean = false, onClick: () -> Unit) {
    FocusCard(onClick, Modifier.width(172.dp), requestFocus = requestFocus) { focused ->
        Column {
            Label(title, size = 15, bold = true, color = if (focused) Palette.Ink else Palette.Text)
            Label(kind, size = 12, color = if (focused) Palette.Ink else Palette.Dim)
        }
    }
}

/**
 * Home, top to bottom: favorites strip, the last three shows played, then tabs for the four
 * ways to browse. It is one scrolling list, so a tab's content can run as long as it needs.
 */
@Composable
fun HomeScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Updater.check(context) }

    val favArtists = Favorites.artists.toList()
    val favTapers = Favorites.tapers.toList()
    val favShows = Favorites.recordings.toList()
    val noFavs = favArtists.isEmpty() && favTapers.isEmpty() && favShows.isEmpty()
    val recents = History.played.take(3)

    var tab by rememberSaveable { mutableStateOf(0) }
    var byYear by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf("") }
    val artists = if (tab == 0) rememberLoad("home-artists") { Archive.topArtists() } else null
    val today = if (tab == 3) rememberLoad("on-this-day") { Archive.onThisDay() } else null
    val thisYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }

    LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(14.dp))
                Label("TaperTV", size = 28, bold = true)
                Spacer(Modifier.weight(1f))
                Updater.available?.let { update ->
                    Chip(
                        Updater.status.ifBlank {
                            (if (update.beta) "Beta update" else "Update") + ": build ${update.build} · install"
                        },
                        onClick = { if (!Updater.busy) scope.launch { Updater.downloadAndInstall(context, update) } },
                        selected = true,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Chip("Settings", onClick = { nav.go(Screen.Settings) })
            }
        }

        // Favorites strip
        item {
            if (noFavs) {
                Label("Favorites show up here. Star a band, follow a taper, or save a show.",
                    size = 13, color = Palette.Dim, maxLines = 2)
            } else {
                LazyRow(horizontalArrangement = gap) {
                    itemsIndexed(favArtists) { i, a ->
                        FavCard(a.name, "Band", requestFocus = i == 0) { nav.go(Screen.ArtistYears(a)) }
                    }
                    itemsIndexed(favTapers) { i, t ->
                        FavCard(t, "Taper", requestFocus = favArtists.isEmpty() && i == 0) { nav.go(Screen.Taper(t)) }
                    }
                    itemsIndexed(favShows) { i, rec ->
                        FavCard(rec.artist.ifBlank { rec.title }, rec.date.ifBlank { "Show" },
                            requestFocus = favArtists.isEmpty() && favTapers.isEmpty() && i == 0) {
                            nav.go(Screen.RecordingPage(rec))
                        }
                    }
                    item { FavCard("All favorites", "Open the full list") { nav.go(Screen.Favorites) } }
                }
            }
        }

        // Recents
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Recents", size = 14, color = Palette.Dim, modifier = Modifier.weight(1f))
                if (History.played.size > 3) Chip("See all", onClick = { nav.go(Screen.History) })
            }
        }
        if (recents.isEmpty()) {
            item { Label("Nothing played yet. Shows you play land here.", size = 13, color = Palette.Dim) }
        } else {
            itemsIndexed(recents, key = { _, p -> "recent-" + p.recording.id }) { i, p ->
                val rec = p.recording
                RowItem(
                    title = listOf(rec.artist, rec.date, rec.place).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { rec.title },
                    trailing = playedWhen(p.at),
                    onClick = { nav.go(Screen.RecordingPage(rec)) },
                    requestFocus = noFavs && i == 0,
                )
            }
        }

        // Tabs
        item {
            Row(horizontalArrangement = gap, modifier = Modifier.padding(top = 4.dp)) {
                homeTabs.forEachIndexed { i, name ->
                    FocusCard(
                        onClick = { tab = i },
                        modifier = Modifier.weight(1f),
                        selected = tab == i,
                        requestFocus = noFavs && recents.isEmpty() && i == 0,
                    ) { focused ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Label(name, size = 16, bold = tab == i,
                                color = if (focused) Palette.Ink else if (tab == i) Palette.Gold else Palette.Text)
                        }
                    }
                }
            }
        }

        when (tab) {
            0 -> {
                item { RowItem("Search every band", onClick = { nav.go(Screen.Artists) }, trailing = "▸") }
                loaded(artists) { list ->
                    list.chunked(4).forEach { row ->
                        item {
                            Row(horizontalArrangement = gap) {
                                row.forEach { a ->
                                    FocusCard(onClick = { nav.go(Screen.ArtistYears(a)) }, modifier = Modifier.weight(1f)) { focused ->
                                        Column {
                                            Label(a.name, size = 15, bold = true, color = if (focused) Palette.Ink else Palette.Text)
                                            Label(if (a.count > 0) "${a.count} recordings" else "Live recordings",
                                                size = 12, color = if (focused) Palette.Ink else Palette.Dim)
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            1 -> {
                item { RowItem("Search tapers by name", onClick = { nav.go(Screen.Tapers) }, trailing = "▸") }
                if (favTapers.isEmpty()) {
                    item {
                        Label("You're not following any tapers yet. Open a show and hit \"More from…\" to find one.",
                            size = 13, color = Palette.Dim, maxLines = 2)
                    }
                } else {
                    itemsIndexed(favTapers) { _, name ->
                        RowItem(name, onClick = { nav.go(Screen.Taper(name)) }, trailing = "Following")
                    }
                }
            }
            2 -> {
                item { Label("Pick a year to see its most-played shows, every band.", size = 13, color = Palette.Dim) }
                (thisYear downTo 1959).toList().chunked(8).forEach { row ->
                    item {
                        Row(horizontalArrangement = gap) {
                            row.forEach { y ->
                                FocusCard(onClick = { nav.go(Screen.DateYear(y)) }, modifier = Modifier.weight(1f)) { focused ->
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Label("$y", size = 15, bold = true, color = if (focused) Palette.Ink else Palette.Text)
                                    }
                                }
                            }
                            repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            else -> {
                item {
                    Row(horizontalArrangement = gap) {
                        Chip("By band", onClick = { byYear = false; open = "" }, selected = !byYear)
                        Chip("By year", onClick = { byYear = true; open = "" }, selected = byYear)
                    }
                }
                loaded(today) { recs ->
                    if (recs.isEmpty()) {
                        item { Label("Nothing in the archive for today's date.", color = Palette.Dim) }
                    } else {
                        daySections(recs, byYear).forEach { section ->
                            val isOpen = open == section.title
                            val n = section.shows.size
                            item {
                                RowItem(
                                    title = section.title,
                                    onClick = { open = if (isOpen) "" else section.title },
                                    trailing = (if (n == 1) "1 show" else "$n shows") + if (isOpen) "  ▾" else "  ▸",
                                    selected = isOpen,
                                )
                            }
                            if (isOpen) {
                                itemsIndexed(section.shows) { _, group ->
                                    Box(Modifier.padding(start = 28.dp)) { ShowGroupRow(group, nav) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Every band's shows from one year, most-played first. Reached from Home's Date tab. */
@Composable
fun DateYearScreen(year: Int, nav: Nav) {
    val load = rememberLoad("date-year", year) { Archive.topShowsOfYear(year) }
    Column {
        ScreenTitle("$year", "The year's most-played shows, every band")
        LoadBox(load) { recs ->
            if (recs.isEmpty()) Label("Nothing in the archive for $year.", color = Palette.Dim)
            else LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                itemsIndexed(groupByShow(recs)) { i, group -> ShowGroupRow(group, nav, requestFocus = i == 0) }
            }
        }
    }
}

/** The switches that used to sit at the bottom of Home. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tileStatus by remember { mutableStateOf("") }
    var beta by remember { mutableStateOf(Updater.betaEnabled(context)) }
    var saver by remember { mutableStateOf(SaverMode.load(context)) }
    Column {
        ScreenTitle("Settings", "TaperTV build ${Updater.currentBuild(context)}")
        Column(verticalArrangement = gap) {
            RowItem(
                "Beta builds",
                subtitle = "Get preview builds through the Update button before everyone else.",
                trailing = if (beta) "On" else "Off",
                selected = beta,
                requestFocus = true,
                onClick = {
                    beta = !beta
                    Updater.setBeta(context, beta)
                    scope.launch { Updater.check(context) }
                },
            )
            RowItem(
                "Screensaver",
                subtitle = "What plays after two idle minutes of music.",
                trailing = saver.label,
                onClick = {
                    saver = SaverMode.values()[(saver.ordinal + 1) % SaverMode.values().size]
                    SaverMode.save(context, saver)
                },
            )
            RowItem(
                "Save launcher tile to Pictures",
                subtitle = tileStatus.ifBlank { "For custom launchers: a transparent tile image you can set as this app's icon." },
                onClick = { tileStatus = TileExport.save(context) },
            )
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

/** One collapsible group on On This Day: a band or a year, and its shows. */
private data class DaySection(val title: String, val shows: List<ShowGroup>)

private fun plays(shows: List<ShowGroup>): Long = shows.sumOf { g -> g.versions.sumOf { it.downloads } }

/** Bands with the most-played tapes first; years newest first. */
private fun daySections(recs: List<Recording>, byYear: Boolean): List<DaySection> {
    val shows = groupByShow(recs)
    return if (byYear) {
        shows.groupBy { it.best.year.ifBlank { "Unknown year" } }
            .map { (year, list) -> DaySection(year, list.sortedByDescending { plays(listOf(it)) }) }
            .sortedWith(compareBy<DaySection> { !it.title.first().isDigit() }.thenByDescending { it.title })
    } else {
        shows.groupBy { it.best.artist.trim().lowercase() }
            .map { (_, list) ->
                DaySection(list.first().best.artist.trim().ifBlank { "Unknown artist" },
                    list.sortedByDescending { it.best.date })
            }
            .sortedWith(compareByDescending<DaySection> { plays(it.shows) }.thenBy { it.title.lowercase() })
    }
}

@Composable
fun OnThisDayScreen(nav: Nav) {
    val load = rememberLoad("on-this-day") { Archive.onThisDay() }
    var byYear by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf("") }
    Column {
        ScreenTitle("On This Day", "Shows played on today's date. Pick a band or a year to open it.")
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = gap) {
            Chip("By band", onClick = { byYear = false; open = "" }, selected = !byYear)
            Chip("By year", onClick = { byYear = true; open = "" }, selected = byYear)
        }
        LoadBox(load) { recs ->
            if (recs.isEmpty()) Label("Nothing in the archive for today's date.", color = Palette.Dim)
            else {
                val sections = remember(recs, byYear) { daySections(recs, byYear) }
                val total = sections.sumOf { it.shows.size }
                Label("$total shows · ${sections.size} ${if (byYear) "years" else "bands"}", size = 13,
                    color = Palette.Dim, modifier = Modifier.padding(bottom = 8.dp))
                // One group open at a time, so the list stays a screen or two long.
                LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                    sections.forEachIndexed { i, section ->
                        val isOpen = open == section.title
                        val n = section.shows.size
                        item {
                            RowItem(
                                title = section.title,
                                onClick = { open = if (isOpen) "" else section.title },
                                trailing = (if (n == 1) "1 show" else "$n shows") + if (isOpen) "  ▾" else "  ▸",
                                selected = isOpen,
                                requestFocus = i == 0,
                            )
                        }
                        if (isOpen) {
                            itemsIndexed(section.shows) { _, group ->
                                Box(Modifier.padding(start = 28.dp)) { ShowGroupRow(group, nav) }
                            }
                        }
                    }
                }
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

// ---- Recently played -------------------------------------------------------------------

/** "Today", "Yesterday", "3 days ago", then a plain date. */
private fun playedWhen(at: Long): String {
    val day = 24L * 60 * 60 * 1000
    val zone = java.util.TimeZone.getDefault()
    fun dayNumber(t: Long) = (t + zone.getOffset(t)) / day
    val days = dayNumber(System.currentTimeMillis()) - dayNumber(at)
    return when {
        at <= 0L -> ""
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(at))
    }
}

@Composable
fun HistoryScreen(nav: Nav) {
    val played = History.played.toList()
    var confirm by remember { mutableStateOf(false) }
    Column {
        ScreenTitle("Recently Played", "Shows played on this TV, newest first")
        if (played.isEmpty()) {
            Label("Nothing yet. Play a show and it lands here.", color = Palette.Dim)
        } else {
            LazyColumn(state = rememberLazyListState(), verticalArrangement = gap) {
                itemsIndexed(played, key = { _, p -> p.recording.id }) { i, p ->
                    val rec = p.recording
                    RowItem(
                        title = listOf(rec.artist, rec.date).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { rec.title },
                        subtitle = listOf(rec.place, if (rec.taper.isBlank()) "" else "Taper: ${rec.taper}")
                            .filter { it.isNotBlank() }.joinToString("  —  "),
                        trailing = playedWhen(p.at),
                        onClick = { nav.go(Screen.RecordingPage(rec)) },
                        requestFocus = i == 0,
                    )
                }
                item {
                    Row(Modifier.padding(top = 8.dp)) {
                        Chip(
                            if (confirm) "Press again to clear the whole list" else "Clear list",
                            onClick = { if (confirm) History.clear() else confirm = true },
                            selected = confirm,
                        )
                    }
                }
            }
        }
    }
}
