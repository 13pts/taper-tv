package net.thirteenpoints.tapertv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import net.thirteenpoints.tapertv.data.Favorites
import net.thirteenpoints.tapertv.playback.PlayerConnection
import net.thirteenpoints.tapertv.ui.App
import net.thirteenpoints.tapertv.ui.Idle

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerConnection

    // Every remote press counts as activity. While the screensaver is up, any press just closes it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Idle.saver) {
            if (event.action == KeyEvent.ACTION_UP && android.os.SystemClock.elapsedRealtime() > Idle.ignoreUntil) {
                Idle.stop()
            }
            return true
        }
        Idle.touch()
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Favorites.init(this)
        player = PlayerConnection(applicationContext)
        setContent { App(player) }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        // Only the UI's handle goes away; the service keeps the music playing.
        player.release()
        super.onStop()
    }
}
