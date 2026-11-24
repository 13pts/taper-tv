package net.thirteenpoints.tapertv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import net.thirteenpoints.tapertv.data.Favorites
import net.thirteenpoints.tapertv.data.History
import net.thirteenpoints.tapertv.playback.PlayerConnection
import net.thirteenpoints.tapertv.ui.App

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Favorites.init(this)
        History.init(this)
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
