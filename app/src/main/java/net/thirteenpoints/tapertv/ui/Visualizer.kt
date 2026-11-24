package net.thirteenpoints.tapertv.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import net.thirteenpoints.tapertv.playback.Spectrum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The visualizer looks you can cycle through on Now Playing. The choice is remembered. */
enum class Look(val label: String) {
    Bars("Bars"),
    Mirror("Mirror"),
    Scope("Scope"),
    Reels("Reels"),
    Meters("VU meters");

    companion object {
        fun load(context: Context): Look {
            val saved = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("look", null)
            return values().firstOrNull { it.name == saved } ?: Bars
        }

        fun save(context: Context, look: Look) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("look", look.name).apply()
        }
    }
}

private val TapeBrown = Color(0xFF3D291D)

/** [progress] is how far through the track we are, 0..1; the Reels look winds tape across with it. */
@Composable
fun Visualizer(look: Look, progress: Float, modifier: Modifier = Modifier) {
    val levels = remember { FloatArray(Spectrum.BANDS) }
    val peaks = remember { FloatArray(Spectrum.BANDS) }
    val wave = remember { FloatArray(Spectrum.WAVE) }
    val needles = remember { FloatArray(2) }
    val spin = remember { FloatArray(1) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = t
                val fresh = System.nanoTime() - Spectrum.updatedAt < 250_000_000L
                val bands = Spectrum.bands
                for (i in levels.indices) {
                    val target = if (fresh && i < bands.size) bands[i] else 0f
                    levels[i] = if (target > levels[i]) target else max(0f, levels[i] - 0.04f)
                    peaks[i] = if (levels[i] >= peaks[i]) levels[i] else max(0f, peaks[i] - 0.010f)
                }
                val source = Spectrum.wave
                for (i in wave.indices) {
                    val target = if (fresh && i < source.size) source[i] else 0f
                    wave[i] += (target - wave[i]) * 0.6f
                }
                val left = if (fresh) Spectrum.levelLeft else 0f
                val right = if (fresh) Spectrum.levelRight else 0f
                // Needles jump up quickly and fall back slowly, like the real thing.
                needles[0] += (left - needles[0]) * (if (left > needles[0]) 0.35f else 0.08f)
                needles[1] += (right - needles[1]) * (if (right > needles[1]) 0.35f else 0.08f)
                if (fresh) spin[0] = (spin[0] + 90f * dt) % 360f
                frame = t
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frame // reading it here is what redraws the canvas each frame
        when (look) {
            Look.Bars -> bars(levels, peaks, mirrored = false)
            Look.Mirror -> bars(levels, peaks, mirrored = true)
            Look.Scope -> scope(wave)
            Look.Reels -> reels(spin[0], max(needles[0], needles[1]), progress.coerceIn(0f, 1f))
            Look.Meters -> meters(needles)
        }
    }
}

/** Segmented spectrum bars with falling peak caps; mirrored grows both ways from the middle. */
private fun DrawScope.bars(levels: FloatArray, peaks: FloatArray, mirrored: Boolean) {
    val count = levels.size
    val gap = 4.dp.toPx()
    val barWidth = (size.width - gap * (count - 1)) / count
    val segHeight = 5.dp.toPx()
    val segGap = 2.dp.toPx()
    val step = segHeight + segGap
    val span = if (mirrored) size.height / 2f else size.height
    val rows = (span / step).toInt().coerceAtLeast(1)
    val base = if (mirrored) size.height / 2f else size.height
    for (i in 0 until count) {
        val x = i * (barWidth + gap)
        val lit = (levels[i] * rows).toInt()
        for (r in 0 until rows) {
            val color = if (r < lit) lerp(Palette.Gold, Palette.Flame, r.toFloat() / rows) else Palette.Panel
            drawRect(color, Offset(x, base - (r + 1) * step + segGap), Size(barWidth, segHeight))
            if (mirrored) drawRect(color, Offset(x, base + r * step + segGap), Size(barWidth, segHeight))
        }
        if (peaks[i] > 0.02f) {
            val peakRow = (peaks[i] * rows).toInt().coerceIn(0, rows - 1)
            val cap = Size(barWidth, segHeight * 0.6f)
            drawRect(Palette.Text, Offset(x, base - (peakRow + 1) * step + segGap), cap)
            if (mirrored) drawRect(Palette.Text, Offset(x, base + peakRow * step + segGap), cap)
        }
    }
}

/** Oscilloscope: the waveform itself as one glowing line. */
private fun DrawScope.scope(wave: FloatArray) {
    val mid = size.height / 2f
    val amp = size.height * 0.46f
    drawLine(Palette.Panel, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
    val path = Path()
    for (i in wave.indices) {
        val x = size.width * i / (wave.size - 1)
        val y = mid - (wave[i] * 2.2f).coerceIn(-1f, 1f) * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Palette.Flame.copy(alpha = 0.35f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, Palette.Gold, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

/** The TaperTV cassette: reels turn while music plays and tape winds from left to right. */
private fun DrawScope.reels(spinDegrees: Float, level: Float, progress: Float) {
    val w = min(size.width, size.height * 1.6f) * 0.94f
    val h = w / 1.6f
    val ox = (size.width - w) / 2f
    val oy = (size.height - h) / 2f
    drawRoundRect(Palette.Gold, Offset(ox, oy), Size(w, h), CornerRadius(w * 0.03f, w * 0.03f))
    // label
    val lx = ox + w * 0.06f
    val ly = oy + h * 0.09f
    val lw = w * 0.88f
    val lh = h * 0.62f
    drawRoundRect(Palette.Text, Offset(lx, ly), Size(lw, lh), CornerRadius(w * 0.012f, w * 0.012f))
    // reel housing
    val cw = w * 0.56f
    val ch = h * 0.24f
    val cx = ox + (w - cw) / 2f
    val cy = ly + lh * 0.52f - ch / 2f
    drawRoundRect(Palette.Ink, Offset(cx, cy), Size(cw, ch), CornerRadius(ch / 2f, ch / 2f))
    drawRoundRect(
        Palette.Flame.copy(alpha = (level * 0.9f).coerceIn(0f, 1f)),
        Offset(cx, cy), Size(cw, ch), CornerRadius(ch / 2f, ch / 2f),
        style = Stroke(width = 4.dp.toPx()),
    )
    val reelRadius = ch * 0.36f
    val leftCentre = Offset(cx + ch / 2f, cy + ch / 2f)
    val rightCentre = Offset(cx + cw - ch / 2f, cy + ch / 2f)
    // tape window, with the pack moving from the left spool to the right one
    val winX = leftCentre.x + reelRadius * 1.5f
    val winW = rightCentre.x - reelRadius * 1.5f - winX
    val winH = ch * 0.52f
    val winY = cy + (ch - winH) / 2f
    drawRect(Palette.Text, Offset(winX, winY), Size(winW, winH))
    val packMax = winW * 0.46f
    val leftPack = packMax * (0.15f + 0.85f * (1f - progress))
    val rightPack = packMax * (0.15f + 0.85f * progress)
    drawRect(TapeBrown, Offset(winX, winY), Size(leftPack, winH))
    drawRect(TapeBrown, Offset(winX + winW - rightPack, winY), Size(rightPack, winH))
    for (centre in listOf(leftCentre, rightCentre)) {
        drawCircle(Palette.Text, reelRadius, centre)
        rotate(spinDegrees, centre) {
            for (k in 0 until 6) {
                rotate(k * 60f, centre) {
                    drawRect(
                        Palette.Ink,
                        Offset(centre.x - reelRadius * 0.11f, centre.y - reelRadius),
                        Size(reelRadius * 0.22f, reelRadius * 0.34f),
                    )
                }
            }
        }
        drawCircle(Palette.Ink, reelRadius * 0.16f, centre)
    }
    // the lip along the bottom edge
    val lipTop = oy + h * 0.80f
    val stroke = 2.dp.toPx()
    drawLine(Palette.Ink, Offset(ox + w * 0.18f, oy + h), Offset(ox + w * 0.22f, lipTop), stroke)
    drawLine(Palette.Ink, Offset(ox + w * 0.22f, lipTop), Offset(ox + w * 0.78f, lipTop), stroke)
    drawLine(Palette.Ink, Offset(ox + w * 0.78f, lipTop), Offset(ox + w * 0.82f, oy + h), stroke)
    drawCircle(Palette.Ink, h * 0.022f, Offset(ox + w * 0.30f, oy + h * 0.91f))
    drawCircle(Palette.Ink, h * 0.022f, Offset(ox + w * 0.70f, oy + h * 0.91f))
}

/** A pair of tape-deck VU meters, left and right channel. */
private fun DrawScope.meters(needles: FloatArray) {
    val gap = size.width * 0.04f
    val boxW = (size.width - gap) / 2f
    val boxH = min(size.height, boxW * 0.72f)
    val top = (size.height - boxH) / 2f
    for (m in 0 until 2) {
        val left = m * (boxW + gap)
        drawRoundRect(Palette.Text, Offset(left, top), Size(boxW, boxH), CornerRadius(12.dp.toPx(), 12.dp.toPx()))
        val pivot = Offset(left + boxW / 2f, top + boxH * 0.92f)
        val radius = boxH * 0.72f
        val arcBox = Size(radius * 2f, radius * 2f)
        val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
        val scale = Stroke(width = 5.dp.toPx())
        // dial runs from 215 to 325 degrees across the top; the last quarter is the red zone
        drawArc(Palette.Ink, 215f, 82f, false, arcTopLeft, arcBox, style = scale)
        drawArc(Color(0xFFCA363B), 297f, 28f, false, arcTopLeft, arcBox, style = scale)
        for (t in 0..10) {
            val a = (215f + 110f * t / 10f) * (PI.toFloat() / 180f)
            val inner = radius * (if (t % 5 == 0) 0.86f else 0.92f)
            drawLine(
                if (t >= 8) Color(0xFFCA363B) else Palette.Ink,
                Offset(pivot.x + inner * cos(a), pivot.y + inner * sin(a)),
                Offset(pivot.x + radius * cos(a), pivot.y + radius * sin(a)),
                strokeWidth = 2.dp.toPx(),
            )
        }
        val angle = (215f + 110f * needles[m].coerceIn(0f, 1f)) * (PI.toFloat() / 180f)
        drawLine(
            Palette.Ink, pivot,
            Offset(pivot.x + radius * 0.96f * cos(angle), pivot.y + radius * 0.96f * sin(angle)),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
        )
        drawCircle(Palette.Ink, boxH * 0.05f, pivot)
    }
}
