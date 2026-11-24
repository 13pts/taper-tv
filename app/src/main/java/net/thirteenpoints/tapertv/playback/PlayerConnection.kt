package net.thirteenpoints.tapertv.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import net.thirteenpoints.tapertv.data.History
import net.thirteenpoints.tapertv.data.RecordingDetail

/** The UI's handle on the playback service, exposed as Compose state. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null

    var hasMedia by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var trackTitle by mutableStateOf("")
        private set
    var showLine by mutableStateOf("")
        private set
    var trackIndex by mutableIntStateOf(-1)
        private set
    var recordingId by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Track titles of whatever is queued, read back from the player so it survives an app restart. */
    var queue by mutableStateOf<List<String>>(emptyList())
        private set
    private var queueFor: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
        override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
            error = "Couldn't play that track (${e.errorCodeName})"
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync()
            pending?.invoke()
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private fun sync() {
        val c = controller ?: return
        hasMedia = c.mediaItemCount > 0
        isPlaying = c.isPlaying
        isBuffering = c.playbackState == Player.STATE_BUFFERING
        trackIndex = if (hasMedia) c.currentMediaItemIndex else -1
        val md = c.mediaMetadata
        trackTitle = md.title?.toString().orEmpty()
        showLine = listOfNotNull(md.artist?.toString(), md.albumTitle?.toString())
            .filter { it.isNotBlank() }.joinToString(" · ")
        recordingId = c.currentMediaItem?.mediaMetadata?.extras?.getString("recordingId") ?: recordingId
        if (!hasMedia) recordingId = null
        if (queue.size != c.mediaItemCount || queueFor != recordingId) {
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaMetadata.title?.toString().orEmpty() }
            queueFor = recordingId
        }
        tick()
        if (c.isPlaying) error = null
    }

    /** Called on a timer by the UI to move the progress bar. */
    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition.coerceAtLeast(0L)
        durationMs = c.duration.takeIf { it > 0 } ?: 0L
    }

    fun play(detail: RecordingDetail, startIndex: Int) {
        val rec = detail.recording
        val album = listOf(rec.date, rec.venue).filter { it.isNotBlank() }.joinToString(" ")
        val items = detail.tracks.map { t ->
            val extras = android.os.Bundle().apply { putString("recordingId", rec.id) }
            MediaItem.Builder()
                .setMediaId(t.url)
                .setUri(t.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(rec.artist)
                        .setAlbumTitle(album)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }
        if (items.isEmpty()) return
        History.add(rec)
        val start: () -> Unit = {
            controller?.let { c ->
                error = null
                recordingId = rec.id
                c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
                c.prepare()
                c.play()
            }
        }
        if (controller != null) start() else { pending = start; connect() }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L))
    }
}
