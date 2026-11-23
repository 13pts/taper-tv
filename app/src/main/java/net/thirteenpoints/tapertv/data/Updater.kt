package net.thirteenpoints.tapertv.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from the repo's rolling "latest" GitHub release. CI stamps each build with its run
 * number as the version code and puts the same number in the release title, so "is there a newer
 * build" is one number compared against another.
 */
object Updater {
    data class Update(val build: Int, val url: String, val beta: Boolean = false)

    private const val LATEST = "https://api.github.com/repos/13pts/taper-tv/releases/latest"
    private const val BETA = "https://api.github.com/repos/13pts/taper-tv/releases/tags/beta"

    /** Whether this TV also takes preview builds from the beta channel. Off unless switched on. */
    fun betaEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("beta", false)

    fun setBeta(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("beta", on).apply()
    }
    private const val UA = "TaperTV-Updater"

    var available by mutableStateOf<Update?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set

    fun currentBuild(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toInt()
    }.getOrDefault(0)

    /** Quiet check: any failure just means no update button this time. */
    suspend fun check(context: Context) {
        if (busy) return
        val found = withContext(Dispatchers.IO) {
            val stable = release(LATEST, beta = false)
            val preview = if (betaEnabled(context)) release(BETA, beta = true) else null
            // Build numbers only ever go up, across both channels, so the higher one is the newer one.
            listOfNotNull(stable, preview).maxByOrNull { it.build }?.takeIf { it.build > currentBuild(context) }
        }
        available = found
        if (!busy) status = ""
    }

    private fun release(endpoint: String, beta: Boolean): Update? = runCatching {
        val json = JSONObject(open(endpoint).inputStream.bufferedReader().use { it.readText() })
        val build = Regex("build (\\d+)").find(json.optString("name"))?.groupValues?.get(1)?.toInt()
            ?: return@runCatching null
        val assets = json.optJSONArray("assets") ?: return@runCatching null
        val url = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?: return@runCatching null
        Update(build, url, beta)
    }.getOrNull()

    suspend fun downloadAndInstall(context: Context, update: Update) {
        if (busy) return
        busy = true
        status = "Downloading build ${update.build}…"
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "taper-tv.apk")
                val conn = open(update.url)
                val total = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = -1L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) done * 100 / total else -1L
                            if (pct != lastShown && pct >= 0) {
                                lastShown = pct
                                status = "Downloading build ${update.build}… $pct%"
                            }
                        }
                    }
                }
                out
            }.getOrNull()
        }
        busy = false
        if (file == null || file.length() < 1_000_000) {
            status = "Download failed. Select to try again."
            return
        }
        status = "Build ${update.build} downloaded · select to install"
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { status = "Couldn't open the installer. Select to try again." }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw java.io.IOException("HTTP $code")
        }
        return conn
    }
}
