package net.thirteenpoints.tapertv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import net.thirteenpoints.tapertv.data.Recording
import net.thirteenpoints.tapertv.playback.PlayerConnection
import net.thirteenpoints.tapertv.playback.Spectrum
import kotlin.math.max

@Composable
fun NowPlayingScreen(player: PlayerConnection, nav: Nav) {
    val listState = rememberLazyListState()
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

            Visualizer(Modifier.fillMaxWidth().weight(1f).padding(vertical = 18.dp))

            val fraction = if (player.durationMs > 0) {
                (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
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

/** Segmented spectrum bars with falling peak caps, in the bolt's gold-to-flame run. */
@Composable
fun Visualizer(modifier: Modifier = Modifier) {
    val levels = remember { FloatArray(Spectrum.BANDS) }
    val peaks = remember { FloatArray(Spectrum.BANDS) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                val source = Spectrum.bands
                val fresh = System.nanoTime() - Spectrum.updatedAt < 250_000_000L
                for (i in levels.indices) {
                    val target = if (fresh && i < source.size) source[i] else 0f
                    levels[i] = if (target > levels[i]) target else max(0f, levels[i] - 0.04f)
                    peaks[i] = if (levels[i] >= peaks[i]) levels[i] else max(0f, peaks[i] - 0.010f)
                }
                frame = t
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frame // reading it here is what redraws the canvas each frame
        val count = levels.size
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val segHeight = 5.dp.toPx()
        val segGap = 2.dp.toPx()
        val step = segHeight + segGap
        val rows = (size.height / step).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val x = i * (barWidth + gap)
            val lit = (levels[i] * rows).toInt()
            for (r in 0 until rows) {
                val y = size.height - (r + 1) * step + segGap
                val color = if (r < lit) {
                    lerp(Palette.Gold, Palette.Flame, r.toFloat() / rows)
                } else {
                    Palette.Panel
                }
                drawRect(color, topLeft = Offset(x, y), size = Size(barWidth, segHeight))
            }
            val peakRow = (peaks[i] * rows).toInt().coerceIn(0, rows - 1)
            if (peaks[i] > 0.02f) {
                val y = size.height - (peakRow + 1) * step + segGap
                drawRect(Palette.Text, topLeft = Offset(x, y), size = Size(barWidth, segHeight * 0.6f))
            }
        }
    }
}
