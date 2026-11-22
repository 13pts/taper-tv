package net.thirteenpoints.tapertv.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

data class Artist(val id: String, val name: String, val count: Int = 0)

/** One taped source of one show. A show (artist + date) usually has several. */
data class Recording(
    val id: String,
    val title: String,
    val artist: String,
    val date: String,
    val year: String,
    val venue: String,
    val city: String,
    val taper: String,
    val source: String,
    val rating: Double,
    val downloads: Long,
) {
    val isSoundboard: Boolean
        get() {
            val hay = (id + " " + source).lowercase()
            return "sbd" in hay || "soundboard" in hay || "matrix" in hay
        }
    val place: String
        get() = listOf(venue, city).filter { it.isNotBlank() }.joinToString(", ")
}

data class Track(val title: String, val lengthSec: Int, val url: String)

data class RecordingDetail(
    val recording: Recording,
    val description: String,
    val lineage: String,
    val transferer: String,
    val notes: String,
    val tracks: List<Track>,
)

data class Show(val date: String, val place: String, val sources: List<Recording>)

data class TaperHit(val name: String, val count: Int)

/** Thin client for archive.org's public search + metadata endpoints. Blocking: call from Dispatchers.IO. */
object Archive {
    private const val BASE = "https://archive.org"
    private const val UA = "TaperTV/0.1 (Android TV; https://github.com/13pts/taper-tv)"
    private const val REC_FIELDS =
        "identifier,title,creator,date,year,venue,coverage,taper,source,avg_rating,downloads"
    private const val LIVE = "collection:etree AND mediatype:etree"

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 60
    }

    private fun get(url: String): String {
        synchronized(cache) { cache[url] }?.let { return it }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("archive.org answered HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            synchronized(cache) { cache[url] = body }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun search(q: String, fields: String, sort: String, rows: Int): List<JSONObject> {
        val url = "$BASE/advancedsearch.php?q=${enc(q)}&fl=${enc(fields)}" +
            "&sort=${enc(sort)}&rows=$rows&page=1&output=json"
        val docs = JSONObject(get(url)).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        return (0 until docs.length()).mapNotNull { docs.optJSONObject(it) }
    }

    /** archive.org fields are loosely typed: a value can be a string, a number or an array. */
    private fun JSONObject.str(key: String): String {
        val v = opt(key) ?: return ""
        return when (v) {
            is JSONArray -> if (v.length() > 0) v.opt(0)?.toString().orEmpty() else ""
            JSONObject.NULL -> ""
            else -> v.toString()
        }.trim()
    }

    private fun JSONObject.strAll(key: String): List<String> {
        val v = opt(key) ?: return emptyList()
        return when (v) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.opt(it)?.toString()?.trim() }
            JSONObject.NULL -> emptyList()
            else -> listOf(v.toString().trim())
        }.filter { it.isNotBlank() }
    }

    private fun toRecording(d: JSONObject): Recording {
        val date = d.str("date").take(10)
        return Recording(
            id = d.str("identifier"),
            title = d.str("title"),
            artist = d.str("creator"),
            date = date,
            year = d.str("year").ifBlank { date.take(4) },
            venue = d.str("venue"),
            city = d.str("coverage"),
            taper = d.strAll("taper").joinToString(", "),
            source = d.str("source"),
            rating = d.str("avg_rating").toDoubleOrNull() ?: 0.0,
            downloads = d.str("downloads").toDoubleOrNull()?.toLong() ?: 0L,
        )
    }

    /** Strip Solr syntax out of typed text so a stray quote or colon can't break the query. */
    private fun clean(text: String): String =
        text.replace(Regex("[\"():\\[\\]{}^~*?\\\\/!+\\-&|]"), " ").trim().replace(Regex("\\s+"), " ")

    private fun words(text: String): String = clean(text).split(" ").filter { it.isNotBlank() }.joinToString(" AND ")

    private fun phrase(text: String): String = "\"" + text.replace("\"", " ").replace("\\", " ").trim() + "\""

    // ---- Artists -------------------------------------------------------------------------

    fun topArtists(): List<Artist> =
        search("mediatype:collection AND collection:etree", "identifier,title,item_count", "downloads desc", 300)
            .map(::toArtist).filter { it.id.isNotBlank() && it.id != "etree" }

    fun searchArtists(text: String): List<Artist> {
        val w = words(text)
        if (w.isBlank()) return emptyList()
        return search(
            "mediatype:collection AND collection:etree AND title:($w)",
            "identifier,title,item_count", "downloads desc", 60,
        ).map(::toArtist).filter { it.id.isNotBlank() }
    }

    private fun toArtist(d: JSONObject) = Artist(
        id = d.str("identifier"),
        name = d.str("title").ifBlank { d.str("identifier") },
        count = d.str("item_count").toDoubleOrNull()?.toInt() ?: 0,
    )

    /** Years an artist has recordings for, newest first. Two tiny queries instead of paging everything. */
    fun artistYears(artistId: String): List<Int> {
        val q = "collection:$artistId AND mediatype:etree AND year:[1900 TO 2100]"
        val first = search(q, "year", "year asc", 1).firstOrNull()?.str("year")?.toDoubleOrNull()?.toInt()
        val last = search(q, "year", "year desc", 1).firstOrNull()?.str("year")?.toDoubleOrNull()?.toInt()
        if (first == null || last == null) return emptyList()
        return (last downTo first).toList()
    }

    fun showsForYear(artistId: String, year: Int): List<Show> =
        groupShows(search("collection:$artistId AND mediatype:etree AND year:$year", REC_FIELDS, "date asc", 2000)
            .map(::toRecording))

    fun groupShows(recs: List<Recording>): List<Show> =
        recs.filter { it.id.isNotBlank() }
            .groupBy { it.date }
            .map { (date, sources) ->
                Show(date, sources.firstOrNull { it.place.isNotBlank() }?.place.orEmpty(), sources)
            }
            .sortedBy { it.date }

    // ---- Tapers --------------------------------------------------------------------------

    fun byTaper(name: String): List<Recording> =
        search("$LIVE AND taper:${phrase(name)}", REC_FIELDS, "date desc", 1000)
            .map(::toRecording).filter { it.id.isNotBlank() }

    /** There is no taper index to browse, so we search recordings and tally the taper names on them. */
    fun findTapers(text: String): List<TaperHit> {
        val w = words(text)
        if (w.isBlank()) return emptyList()
        val needle = clean(text).lowercase().split(" ")
        return search("$LIVE AND taper:($w)", "taper", "downloads desc", 2000)
            .flatMap { it.strAll("taper") }
            .filter { name -> needle.all { it in name.lowercase() } }
            .groupingBy { it }.eachCount()
            .map { TaperHit(it.key, it.value) }
            .sortedByDescending { it.count }
    }

    // ---- Search / discovery --------------------------------------------------------------

    fun searchRecordings(text: String): List<Recording> {
        val w = words(text)
        if (w.isBlank()) return emptyList()
        return search("$LIVE AND ($w)", REC_FIELDS, "downloads desc", 150)
            .map(::toRecording).filter { it.id.isNotBlank() }
    }

    fun onThisDay(): List<Recording> {
        val cal = Calendar.getInstance()
        val md = "%02d-%02d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val dates = (1959..cal.get(Calendar.YEAR)).joinToString(" OR ") { "date:$it-$md" }
        return search("$LIVE AND ($dates)", REC_FIELDS, "downloads desc", 300)
            .map(::toRecording).filter { it.id.isNotBlank() }
    }

    // ---- One recording -------------------------------------------------------------------

    fun detail(id: String): RecordingDetail {
        val root = JSONObject(get("$BASE/metadata/${Uri.encode(id)}"))
        val m = root.optJSONObject("metadata") ?: throw IOException("That recording isn't on archive.org any more")
        val rec = toRecording(m)
        val files = root.optJSONArray("files") ?: JSONArray()
        val all = (0 until files.length()).mapNotNull { files.optJSONObject(it) }

        fun pick(test: (JSONObject) -> Boolean) = all.filter(test)
        // MP3 first: every Live Music Archive item has it and every TV decodes it.
        val chosen = pick { it.str("format") == "VBR MP3" }
            .ifEmpty { pick { it.str("name").endsWith(".mp3", ignoreCase = true) } }
            .ifEmpty { pick { it.str("format") == "Ogg Vorbis" } }
            .ifEmpty { pick { it.str("format").contains("Flac", ignoreCase = true) } }

        val tracks = chosen
            .sortedWith(compareBy({ trackNo(it.str("track")) }, { it.str("name") }))
            .map { f ->
                val name = f.str("name")
                Track(
                    title = f.str("title").ifBlank { name.substringAfterLast('/').substringBeforeLast('.') },
                    lengthSec = parseLength(f.str("length")),
                    url = "$BASE/download/${Uri.encode(id)}/${Uri.encode(name, "/")}",
                )
            }

        return RecordingDetail(
            recording = rec,
            description = m.str("description"),
            lineage = m.str("lineage"),
            transferer = m.str("transferer"),
            notes = m.str("notes"),
            tracks = tracks,
        )
    }

    private fun trackNo(s: String): Int = s.substringBefore('/').trim().toIntOrNull() ?: Int.MAX_VALUE

    /** Lengths come as "03:05", "1:02:33" or plain seconds like "185.12". */
    private fun parseLength(s: String): Int {
        if (s.isBlank()) return 0
        if (':' !in s) return s.toDoubleOrNull()?.toInt() ?: 0
        return s.split(":").fold(0) { acc, part -> acc * 60 + (part.trim().toDoubleOrNull()?.toInt() ?: 0) }
    }
}
