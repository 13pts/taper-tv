package net.thirteenpoints.tapertv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.thirteenpoints.tapertv.data.Recording
import net.thirteenpoints.tapertv.playback.PlayerConnection
import kotlin.math.max

@Composable
fun NowPlayingScreen(player: PlayerConnection, nav: Nav) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var look by remember { mutableStateOf(Look.load(context)) }
    // Keep the playing track in view as the show moves along.
    LaunchedEffect(player.trackIndex) {
        if (player.trackIndex >= 0) runCatching { listState.animateScrollToItem(max(0, player.trackIndex - 2)) }
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.56f).fillMaxHeight().padding(end = 28.dp)) {
            Label("Now playing", size = 14, color = Palette.Gold, bold = true)
            Spacer(Modifier.height(6.dp))
            Label(player.trackTitle.ifBlank { "Nothing queued" }, size = 30, bold = true, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Label(player.showLine, size = 16, color = Palette.Dim, maxLines = 2)

            val fraction = if (player.durationMs > 0) {
                (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Visualizer(look, fraction, Modifier.fillMaxWidth().weight(1f).padding(vertical = 18.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Palette.Gold,
                trackColor = Palette.Panel,
            )
            Spacer(Modifier.height(6.dp))
            Label(
                clock(player.positionMs / 1000) + " / " + clock(player.durationMs / 1000) +
                    if (player.isBuffering) "  ·  buffering…" else "",
                size = 13, color = Palette.Dim,
            )
            player.error?.let {
                Spacer(Modifier.height(6.dp))
                Label(it, size = 13, color = Palette.Flame, maxLines = 2)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("⏮", onClick = { player.previous() })
                Chip("−30s", onClick = { player.seekBy(-30_000) })
                Chip(if (player.isPlaying) "⏸ Pause" else "▶ Play", onClick = { player.toggle() }, requestFocus = true)
                Chip("+30s", onClick = { player.seekBy(30_000) })
                Chip("⏭", onClick = { player.next() })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Look: ${look.label}", onClick = {
                    look = Look.values()[(look.ordinal + 1) % Look.values().size]
                    Look.save(context, look)
                })
                player.recordingId?.let { id ->
                    Chip("Go to show", onClick = { nav.go(Screen.RecordingPage(stub(id))) })
                }
            }
        }
        Column(Modifier.weight(0.44f).fillMaxHeight()) {
            Label("This show · ${player.queue.size} tracks", size = 13, color = Palette.Dim,
                modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(player.queue) { i, title ->
                    RowItem(
                        title = "${i + 1}. ${title.ifBlank { "Track ${i + 1}" }}",
                        selected = i == player.trackIndex,
                        trailing = if (i == player.trackIndex) "playing" else "",
                        onClick = { player.jumpTo(i) },
                    )
                }
            }
        }
    }
}

/** The recording page loads everything it shows from the id, so an id is all we need to get back. */
private fun stub(id: String) = Recording(
    id = id, title = "", artist = "", date = "", year = "", venue = "", city = "",
    taper = "", source = "", rating = 0.0, downloads = 0L,
)
