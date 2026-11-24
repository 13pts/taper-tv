package net.thirteenpoints.tapertv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.thirteenpoints.tapertv.data.Artist
import net.thirteenpoints.tapertv.data.Recording
import net.thirteenpoints.tapertv.data.Show
import net.thirteenpoints.tapertv.playback.PlayerConnection

sealed interface Screen {
    data object Home : Screen
    data object Artists : Screen
    data object Tapers : Screen
    data object Search : Screen
    data object OnThisDay : Screen
    data object Favorites : Screen
    data object History : Screen
    data object NowPlaying : Screen
    data class ArtistYears(val artist: Artist) : Screen
    data class Year(val artist: Artist, val year: Int) : Screen
    data class ShowSources(val artistName: String, val show: Show) : Screen
    /** [versions] holds every tape of the same show, so the page can offer a switch. */
    data class RecordingPage(val rec: Recording, val versions: List<Recording> = emptyList()) : Screen
    data class Taper(val name: String) : Screen
}

class Nav(private val stack: SnapshotStateList<Screen>) {
    fun go(screen: Screen) {
        stack.add(screen)
    }
}

/** Lets the remote's Back button, held down, ask for Home from outside the UI tree. */
object HomeSignal {
    var count by mutableIntStateOf(0)
        private set

    fun fire() {
        count++
    }
}

@Composable
fun App(player: PlayerConnection) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember { Nav(stack) }
    val holder = rememberSaveableStateHolder()

    BackHandler(enabled = stack.size > 1) {
        val depth = stack.size
        stack.removeAt(stack.lastIndex)
        holder.removeState(depth)
    }

    // Drop every screen above Home in one go, however deep the trail got.
    val goHome: () -> Unit = {
        while (stack.size > 1) {
            val depth = stack.size
            stack.removeAt(stack.lastIndex)
            holder.removeState(depth)
        }
    }
    val homeRequests = HomeSignal.count
    LaunchedEffect(homeRequests) {
        if (homeRequests > 0) goHome()
    }

    LaunchedEffect(Unit) {
        while (true) {
            player.tick()
            delay(500)
        }
    }

    // Two idle minutes with music playing brings up the screensaver. It never starts in silence.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val idleFor = android.os.SystemClock.elapsedRealtime() - Idle.lastInput
            if (!Idle.saver && player.isPlaying && idleFor > 120_000L && SaverMode.load(context) != SaverMode.Off) {
                Idle.start()
            }
            if (Idle.saver && !player.hasMedia) Idle.stop()
        }
    }
    // Hold the screen while ours is up, so the TV's own screensaver doesn't cover it.
    val view = LocalView.current
    val saverOn = Idle.saver
    DisposableEffect(saverOn) {
        view.keepScreenOn = saverOn
        onDispose { view.keepScreenOn = false }
    }

    val colors = darkColorScheme(
        primary = Palette.Gold,
        onPrimary = Palette.Ink,
        background = Palette.Bg,
        onBackground = Palette.Text,
        surface = Palette.Bg,
        onSurface = Palette.Text,
    )
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = Palette.Bg) {
            if (saverOn) {
                Screensaver(player)
            } else {
            // 48 x 27 dp keeps everything inside the TV-safe area.
            val depth = stack.size
            val current = stack[depth - 1]
            val showHome = current !is Screen.Home
            Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                // The screen is composed before the rail, so a screen's own first item still
                // gets focus first. The rail sits to its left, one Left press from any row.
                Column(Modifier.fillMaxSize().padding(start = if (showHome) HomeRailWidth + 14.dp else 0.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        holder.SaveableStateProvider(depth) {
                            ScreenHost(current, nav, player)
                        }
                    }
                    if (player.hasMedia && current !is Screen.NowPlaying) {
                        Spacer(Modifier.height(10.dp))
                        NowPlayingBar(player) { nav.go(Screen.NowPlaying) }
                    }
                }
                if (showHome) HomeRail(goHome, Modifier.align(Alignment.CenterStart))
            }
            }
        }
    }
}

private val HomeRailWidth = 72.dp

/**
 * Home, as a full-height strip down the left edge. Full height on purpose: wherever the
 * remote is in a list, pressing Left lines up with it.
 */
@Composable
private fun HomeRail(goHome: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(goHome, modifier.width(HomeRailWidth).fillMaxHeight()) { focused ->
        val color = if (focused) Palette.Ink else Palette.Dim
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("⌂", size = 24, color = color)
            Label("Home", size = 12, color = color, bold = focused)
        }
    }
}

@Composable
private fun ScreenHost(screen: Screen, nav: Nav, player: PlayerConnection) {
    when (screen) {
        Screen.Home -> HomeScreen(nav)
        Screen.Artists -> ArtistsScreen(nav)
        Screen.Tapers -> TapersScreen(nav)
        Screen.Search -> SearchScreen(nav)
        Screen.OnThisDay -> OnThisDayScreen(nav)
        Screen.Favorites -> FavoritesScreen(nav)
        Screen.History -> HistoryScreen(nav)
        Screen.NowPlaying -> NowPlayingScreen(player, nav)
        is Screen.ArtistYears -> ArtistYearsScreen(screen.artist, nav)
        is Screen.Year -> YearScreen(screen.artist, screen.year, nav)
        is Screen.ShowSources -> ShowSourcesScreen(screen.artistName, screen.show, nav)
        is Screen.RecordingPage -> RecordingScreen(screen.rec, screen.versions, nav, player)
        is Screen.Taper -> TaperScreen(screen.name, nav)
    }
}

@Composable
private fun NowPlayingBar(player: PlayerConnection, open: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FocusCard(open, Modifier.weight(1f)) { focused ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(
                        player.trackTitle.ifBlank { "Now playing" },
                        size = 15, bold = true,
                        color = if (focused) Palette.Ink else Palette.Gold,
                    )
                    Label(player.showLine, size = 12, color = if (focused) Palette.Ink else Palette.Dim)
                }
                Spacer(Modifier.width(12.dp))
                Label(
                    clock(player.positionMs / 1000) + " / " + clock(player.durationMs / 1000),
                    size = 12, color = if (focused) Palette.Ink else Palette.Dim,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Chip(if (player.isPlaying) "Pause" else "Play", onClick = { player.toggle() })
        Spacer(Modifier.width(10.dp))
        Chip("Next", onClick = { player.next() })
    }
}
