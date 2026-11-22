package net.thirteenpoints.tapertv.playback

import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Latest frequency-band levels (0..1), written by the audio thread and read by the visualizer. */
object Spectrum {
    const val BANDS = 28

    @Volatile
    var bands: FloatArray = FloatArray(BANDS)

    @Volatile
    var updatedAt: Long = 0L
}

/**
 * Listens in on the decoded audio on its way to the speakers and turns it into band levels.
 * No microphone permission involved: this reads the app's own audio, not the room.
 */
class SpectrumSink : TeeAudioProcessor.AudioBufferSink {
    private val n = 1024
    private val ring = FloatArray(n)
    private var pos = 0
    private var channels = 2
    private var rate = 44100
    private var lastRun = 0L
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val re = FloatArray(n)
    private val im = FloatArray(n)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        rate = if (sampleRateHz > 0) sampleRateHz else 44100
        channels = max(1, channelCount)
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val samples = buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = samples.remaining() / channels
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += samples.get().toFloat()
            ring[pos] = sum / channels / 32768f
            pos = (pos + 1) % n
        }
        val now = System.nanoTime()
        if (now - lastRun >= 30_000_000L) {
            lastRun = now
            analyse(now)
        }
    }

    private fun analyse(now: Long) {
        for (i in 0 until n) {
            re[i] = ring[(pos + i) % n] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val out = FloatArray(Spectrum.BANDS)
        val lowHz = 45.0
        val highHz = min(16000.0, rate / 2.0 - 1.0)
        val ratio = highHz / lowHz
        for (b in 0 until Spectrum.BANDS) {
            val f0 = lowHz * ratio.pow(b.toDouble() / Spectrum.BANDS)
            val f1 = lowHz * ratio.pow((b + 1).toDouble() / Spectrum.BANDS)
            val k0 = max(1, (f0 * n / rate).toInt())
            val k1 = min(n / 2 - 1, max(k0, (f1 * n / rate).toInt()))
            var peak = 0f
            for (k in k0..k1) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > peak) peak = mag
            }
            // A full-scale tone through a Hann window peaks near n/4.
            val db = 20f * log10(max(peak / (n / 4f), 1e-6f))
            out[b] = ((db + 62f) / 56f).coerceIn(0f, 1f)
        }
        Spectrum.bands = out
        Spectrum.updatedAt = now
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val size = re.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < size) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
