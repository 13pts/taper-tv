package net.thirteenpoints.tapertv.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import net.thirteenpoints.tapertv.R

/**
 * Copies the transparent launcher tile into the TV's Pictures folder, byte for byte, so a custom
 * launcher (Projectivy and friends) can be pointed at it without a phone in the middle.
 */
object TileExport {
    fun save(context: Context): String {
        if (Build.VERSION.SDK_INT < 29) return "This needs Android 10 or newer."
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "tapertv-tile.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching "Couldn't create the file in Pictures."
            resolver.openOutputStream(uri)?.use { out ->
                context.resources.openRawResource(R.raw.tapertv_tile).use { it.copyTo(out) }
            } ?: return@runCatching "Couldn't write the file in Pictures."
            "Saved to Pictures as tapertv-tile.png"
        }.getOrElse { "Couldn't save: ${it.message ?: "unknown error"}" }
    }
}
