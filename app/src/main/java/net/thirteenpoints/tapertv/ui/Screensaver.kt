package net.thirteenpoints.tapertv.ui

import android.content.Context
import android.os.SystemClock
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.thirteenpoints.tapertv.data.Archive
import net.thirteenpoints.tapertv.data.VideoSet
import net.thirteenpoints.tapertv.playback.PlayerConnection

/** Tracks remote activity and whether the screensaver is showing. */
object Idle {
    var saver by mutableStateOf(false)
        private set

    @Volatile
    var lastInput: Long = SystemClock.elapsedRealtime()

    /** The press that starts the screensaver shouldn't also be the press that closes it. */
    @Volatile
    var ignoreUntil: Long = 0L

    fun touch() {
        lastInput = SystemClock.elapsedRealtime()
    }

    fun start() {
        ignoreUntil = SystemClock.elapsedRealtime() + 700L
        saver = true
    }

    fun stop() {
        saver = false
        touch()
    }
}

enum class SaverMode(val label: String, val videoSet: VideoSet?) {
    Nature("Nature video", VideoSet.Nature),
    Vintage("Vintage film", VideoSet.Vintage),
    Looks("Visualizer", null),
    Off("Off", null);

    companion object {
        fun load(context: Context): SaverMode {
            val saved = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("saver", null)
            return values().firstOrNull { it.name == saved } ?: Nature
        }

        fun save(context: Context, mode: SaverMode) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("saver", mode.name).apply()
        }
    }
}

/**
 * Full-screen screensaver: silent footage from archive.org behind the music, or the visualizer
 * if that's the chosen mode or the footage won't load. The caption moves so nothing burns in.
 */
@Composable
fun Screensaver(player: PlayerConnection) {
    val context = LocalContext.current
    val mode = remember { SaverMode.load(context) }
    val look = remember { Look.load(context) }
    var videoFailed by remember { mutableStateOf(false) }
    var drift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(25_000)
            drift++
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val set = mode.videoSet
        if (set != null && !videoFailed) {
            VideoLoop(set, onFailed = { videoFailed = true })
        } else {
            val dx = listOf(0, 18, -14, 10, -20)[drift % 5]
            val dy = listOf(0, -10, 12, -6, 8)[drift % 5]
            val fraction = if (player.durationMs > 0) {
                (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Visualizer(
                look, fraction,
                Modifier.fillMaxSize().padding(horizontal = 90.dp, vertical = 70.dp).offset(dx.dp, dy.dp),
            )
        }
        val corner = when (drift % 4) {
            0 -> Alignment.BottomStart
            1 -> Alignment.TopEnd
            2 -> Alignment.BottomEnd
            else -> Alignment.TopStart
        }
        Column(
            Modifier.align(corner).padding(48.dp)
                .background(Color(0x99000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Label(player.trackTitle.ifBlank { "TaperTV" }, size = 18, bold = true)
            if (player.showLine.isNotBlank()) Label(player.showLine, size = 13, color = Palette.Dim)
        }
    }
}

@Composable
private fun VideoLoop(set: VideoSet, onFailed: () -> Unit) {
    val context = LocalContext.current
    // A second, silent player just for pictures. The music keeps coming from the playback service.
    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }
    var advance by remember { mutableIntStateOf(0) }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) advance++
            }

            override fun onPlayerError(error: PlaybackException) {
                advance++
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    LaunchedEffect(set) {
        val clips = withContext(Dispatchers.IO) {
            runCatching { Archive.videoClips(set) }.getOrDefault(emptyList())
        }.shuffled()
        if (clips.isEmpty()) {
            onFailed()
            return@LaunchedEffect
        }
        var misses = 0
        var index = 0
        while (true) {
            val clip = clips[index % clips.size]
            index++
            val url = withContext(Dispatchers.IO) { runCatching { Archive.videoUrl(clip.id) }.getOrNull() }
            if (url == null) {
                misses++
                if (misses >= 4) {
                    onFailed()
                    return@LaunchedEffect
                }
                continue
            }
            val before = advance
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
            // Stay on this clip until it ends, fails, or three minutes pass, then move on.
            withTimeoutOrNull(180_000L) { snapshotFlow { advance }.first { it != before } }
            if (exo.playerError != null) {
                misses++
                if (misses >= 4) {
                    onFailed()
                    return@LaunchedEffect
                }
            } else {
                misses = 0
            }
        }
    }
    AndroidView(
        factory = { ctx -> SurfaceView(ctx).also { exo.setVideoSurfaceView(it) } },
        modifier = Modifier.fillMaxSize(),
    )
}
