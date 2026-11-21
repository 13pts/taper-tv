package net.thirteenpoints.tapertv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Favorite artists, tapers and recordings, kept on the device. */
object Favorites {
    val artists = mutableStateListOf<Artist>()
    val tapers = mutableStateListOf<String>()
    val recordings = mutableStateListOf<Recording>()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            val a = JSONArray(p.getString("artists", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                artists.add(Artist(o.getString("id"), o.getString("name"), o.optInt("count")))
            }
            val t = JSONArray(p.getString("tapers", "[]"))
            for (i in 0 until t.length()) tapers.add(t.getString(i))
            val r = JSONArray(p.getString("recordings", "[]"))
            for (i in 0 until r.length()) {
                val o = r.getJSONObject(i)
                recordings.add(
                    Recording(
                        id = o.getString("id"), title = o.optString("title"), artist = o.optString("artist"),
                        date = o.optString("date"), year = o.optString("year"), venue = o.optString("venue"),
                        city = o.optString("city"), taper = o.optString("taper"), source = o.optString("source"),
                        rating = o.optDouble("rating", 0.0), downloads = o.optLong("downloads"),
                    )
                )
            }
        }
    }

    fun hasArtist(id: String) = artists.any { it.id == id }
    fun hasTaper(name: String) = tapers.any { it.equals(name, ignoreCase = true) }
    fun hasRecording(id: String) = recordings.any { it.id == id }

    fun toggle(artist: Artist) {
        if (!artists.removeAll { it.id == artist.id }) artists.add(0, artist)
        save()
    }

    fun toggleTaper(name: String) {
        if (!tapers.removeAll { it.equals(name, ignoreCase = true) }) tapers.add(0, name)
        save()
    }

    fun toggle(rec: Recording) {
        if (!recordings.removeAll { it.id == rec.id }) recordings.add(0, rec)
        save()
    }

    private fun save() {
        val a = JSONArray()
        artists.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("count", it.count)) }
        val t = JSONArray()
        tapers.forEach { t.put(it) }
        val r = JSONArray()
        recordings.forEach {
            r.put(
                JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                    .put("date", it.date).put("year", it.year).put("venue", it.venue).put("city", it.city)
                    .put("taper", it.taper).put("source", it.source).put("rating", it.rating)
                    .put("downloads", it.downloads)
            )
        }
        prefs?.edit()
            ?.putString("artists", a.toString())
            ?.putString("tapers", t.toString())
            ?.putString("recordings", r.toString())
            ?.apply()
    }
}
