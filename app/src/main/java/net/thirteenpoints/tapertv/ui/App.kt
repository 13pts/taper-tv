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
    data object NowPlaying : Screen
    data class ArtistYears(val artist: Artist) : Screen
    data class Year(val artist: Artist, val year: Int) : Screen
    data class ShowSources(val artistName: String, val show: Show) : Screen
    data class RecordingPage(val rec: Recording) : Screen
    data class Taper(val name: String) : Screen
}

class Nav(private val stack: SnapshotStateList<Screen>) {
    fun go(screen: Screen) {
        stack.add(screen)
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

    LaunchedEffect(Unit) {
        while (true) {
            player.tick()
            delay(500)
        }
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
            // 48 x 27 dp keeps everything inside the TV-safe area.
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                val depth = stack.size
                val current = stack[depth - 1]
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
        Screen.NowPlaying -> NowPlayingScreen(player)
        is Screen.ArtistYears -> ArtistYearsScreen(screen.artist, nav)
        is Screen.Year -> YearScreen(screen.artist, screen.year, nav)
        is Screen.ShowSources -> ShowSourcesScreen(screen.artistName, screen.show, nav)
        is Screen.RecordingPage -> RecordingScreen(screen.rec, nav, player)
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
