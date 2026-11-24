package net.thirteenpoints.tapertv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** One show you pressed play on, and when. */
data class Played(val recording: Recording, val at: Long)

/** The shows played on this device, newest first. Kept on the device, like favorites. */
object History {
    private const val MAX = 100
    val played = mutableStateListOf<Played>()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            val a = JSONArray(p.getString("played", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                played.add(
                    Played(
                        Recording(
                            id = o.getString("id"), title = o.optString("title"), artist = o.optString("artist"),
                            date = o.optString("date"), year = o.optString("year"), venue = o.optString("venue"),
                            city = o.optString("city"), taper = o.optString("taper"), source = o.optString("source"),
                            rating = o.optDouble("rating", 0.0), downloads = o.optLong("downloads"),
                        ),
                        o.optLong("at"),
                    )
                )
            }
        }
    }

    /** Moves the show to the top, so playing it again doesn't list it twice. */
    fun add(rec: Recording) {
        played.removeAll { it.recording.id == rec.id }
        played.add(0, Played(rec, System.currentTimeMillis()))
        while (played.size > MAX) played.removeAt(played.lastIndex)
        save()
    }

    fun remove(id: String) {
        if (played.removeAll { it.recording.id == id }) save()
    }

    fun clear() {
        played.clear()
        save()
    }

    private fun save() {
        val a = JSONArray()
        played.forEach { (r, at) ->
            a.put(
                JSONObject().put("id", r.id).put("title", r.title).put("artist", r.artist)
                    .put("date", r.date).put("year", r.year).put("venue", r.venue).put("city", r.city)
                    .put("taper", r.taper).put("source", r.source).put("rating", r.rating)
                    .put("downloads", r.downloads).put("at", at)
            )
        }
        prefs?.edit()?.putString("played", a.toString())?.apply()
    }
}
