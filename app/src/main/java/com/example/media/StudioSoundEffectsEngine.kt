package com.example.media

import android.content.Context
import com.example.data.model.AudioRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class StudioAudioPreset(
    val id: String,
    val name: String,
    val role: AudioRole,
    val durationMs: Long,
    val subtitle: String
)

object StudioAudioCatalog {
    val musicPresets: List<StudioAudioPreset> = listOf(
        StudioAudioPreset(
            id = "bgm_midnight_synth",
            name = "Midnight Synth Pad",
            role = AudioRole.MUSIC,
            durationMs = 12000L,
            subtitle = "Lush cinematic synthwave chords"
        ),
        StudioAudioPreset(
            id = "bgm_lofi_rhodes",
            name = "Lo-Fi Rhodes Groove",
            role = AudioRole.MUSIC,
            durationMs = 10000L,
            subtitle = "Warm electric piano chord bed"
        ),
        StudioAudioPreset(
            id = "bgm_pulse_action",
            name = "Pulse Action Bass",
            role = AudioRole.MUSIC,
            durationMs = 8000L,
            subtitle = "Driving rhythmic low-end pulse"
        ),
        StudioAudioPreset(
            id = "bgm_acoustic_horizon",
            name = "Acoustic Horizon",
            role = AudioRole.MUSIC,
            durationMs = 10000L,
            subtitle = "Bright plucked melodic arpeggio"
        )
    )

    val sfxPresets: List<StudioAudioPreset> = listOf(
        StudioAudioPreset(
            id = "sfx_whoosh",
            name = "Whoosh Sweep",
            role = AudioRole.SFX,
            durationMs = 1200L,
            subtitle = "Airy cut transition swoosh"
        ),
        StudioAudioPreset(
            id = "sfx_cinema_boom",
            name = "Cinema Boom",
            role = AudioRole.SFX,
            durationMs = 1800L,
            subtitle = "Deep sub-bass trailer impact"
        ),
        StudioAudioPreset(
            id = "sfx_shutter",
            name = "Camera Shutter",
            role = AudioRole.SFX,
            durationMs = 550L,
            subtitle = "Crisp mechanical dual-click"
        ),
        StudioAudioPreset(
            id = "sfx_glitch_zap",
            name = "Glitch Zap",
            role = AudioRole.SFX,
            durationMs = 850L,
            subtitle = "Cyber FM digital pulse"
        ),
        StudioAudioPreset(
            id = "sfx_sparkle_chime",
            name = "Sparkle Chime",
            role = AudioRole.SFX,
            durationMs = 1400L,
            subtitle = "Bright harmonic accent bell"
        ),
        StudioAudioPreset(
            id = "sfx_riser",
            name = "Tension Riser",
            role = AudioRole.SFX,
            durationMs = 2400L,
            subtitle = "Ascending cinematic swell"
        ),
        StudioAudioPreset(
            id = "sfx_pop",
            name = "UI Pop Accent",
            role = AudioRole.SFX,
            durationMs = 500L,
            subtitle = "Clean resonant caption pop"
        ),
        StudioAudioPreset(
            id = "sfx_rain_bed",
            name = "Rain Ambience",
            role = AudioRole.SFX,
            durationMs = 6000L,
            subtitle = "Soft atmospheric rain texture"
        )
    )

    fun findById(id: String): StudioAudioPreset? =
        (musicPresets + sfxPresets).firstOrNull { it.id == id }
}

/**
 * Generates and caches real playable 16-bit PCM WAV files for built-in studio
 * background music beds and sound effects so timeline preview & auditions play real audio.
 */
class StudioSoundEffectsEngine(private val context: Context) {

    private val sampleRate = 22050

    suspend fun ensurePresetFilePath(preset: StudioAudioPreset): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "studio_audio").apply { mkdirs() }
        val file = File(dir, "${preset.id}.wav")
        if (!file.exists() || file.length() < 128L) {
            val samples = synthesizePreset(preset)
            writePcm16MonoWav(file, samples, sampleRate)
        }
        file.absolutePath
    }

    private fun synthesizePreset(preset: StudioAudioPreset): ShortArray {
        val totalSamples = ((preset.durationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(sampleRate / 4)
        val out = DoubleArray(totalSamples)
        val rng = Random(preset.id.hashCode())

        when (preset.id) {
            "bgm_midnight_synth" -> {
                // 4-bar lush synthwave progression: Am9 -> Fmaj7 -> Cmaj7 -> G6
                val chords = listOf(
                    doubleArrayOf(220.0, 261.63, 329.63, 392.0, 493.88),
                    doubleArrayOf(174.61, 220.0, 261.63, 329.63, 392.0),
                    doubleArrayOf(196.0, 261.63, 329.63, 392.0, 493.88),
                    doubleArrayOf(196.0, 246.94, 293.66, 392.0, 440.0)
                )
                val barSamples = totalSamples / chords.size
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val barIdx = (i / barSamples).coerceIn(0, chords.lastIndex)
                    val barPos = (i % barSamples).toDouble() / barSamples
                    val chord = chords[barIdx]
                    val barEnv = smoothEdgeEnvelope(barPos, 0.08, 0.12)
                    var sum = 0.0
                    for (f in chord) {
                        sum += sin(2.0 * PI * f * t) * 0.55
                        sum += sin(2.0 * PI * (f * 1.004) * t) * 0.25
                        sum += sin(2.0 * PI * (f * 0.5) * t) * 0.20
                    }
                    // Subtle warm LFO shimmer
                    val lfo = 0.85 + 0.15 * sin(2.0 * PI * 1.5 * t)
                    out[i] = (sum / chord.size) * barEnv * lfo * 0.65
                }
            }

            "bgm_lofi_rhodes" -> {
                val chords = listOf(
                    doubleArrayOf(261.63, 329.63, 392.0, 493.88),
                    doubleArrayOf(220.0, 261.63, 329.63, 392.0),
                    doubleArrayOf(293.66, 349.23, 440.0, 523.25),
                    doubleArrayOf(196.0, 246.94, 349.23, 440.0)
                )
                val barSamples = totalSamples / chords.size
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val barIdx = (i / barSamples).coerceIn(0, chords.lastIndex)
                    val localT = (i % barSamples).toDouble() / sampleRate
                    val chord = chords[barIdx]
                    val decay = exp(-localT * 0.85) * (1.0 - exp(-localT * 45.0))
                    var sum = 0.0
                    for (f in chord) {
                        val trem = 0.90 + 0.10 * sin(2.0 * PI * 4.2 * t)
                        sum += (sin(2.0 * PI * f * t) + 0.25 * sin(2.0 * PI * f * 2.0 * t) * exp(-localT * 3.0)) * trem
                    }
                    // Soft lo-fi pulse kick every 0.625s
                    val beatT = t % 0.625
                    val kick = sin(2.0 * PI * (95.0 * exp(-beatT * 14.0) + 42.0) * beatT) * exp(-beatT * 18.0) * 0.35
                    out[i] = ((sum / chord.size) * decay * 0.65 + kick)
                }
            }

            "bgm_pulse_action" -> {
                val rootPattern = doubleArrayOf(82.41, 82.41, 98.0, 110.0, 82.41, 82.41, 123.47, 110.0)
                val stepDur = 0.25
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val stepIdx = ((t / stepDur).toInt()) % rootPattern.size
                    val localT = t % stepDur
                    val f = rootPattern[stepIdx]
                    val env = exp(-localT * 7.5) * (1.0 - exp(-localT * 120.0))
                    val bass = sin(2.0 * PI * f * t) * 0.65 +
                        sin(2.0 * PI * f * 2.0 * t) * 0.35 +
                        sin(2.0 * PI * f * 3.0 * t) * 0.15
                    // High rhythmic pulse tick
                    val hiHat = if (localT < 0.03) (rng.nextDouble() * 2.0 - 1.0) * exp(-localT * 90.0) * 0.18 else 0.0
                    out[i] = bass * env * 0.72 + hiHat
                }
            }

            "bgm_acoustic_horizon" -> {
                val notes = doubleArrayOf(
                    261.63, 329.63, 392.0, 523.25, 392.0, 329.63,
                    293.66, 349.23, 440.0, 587.33, 440.0, 349.23,
                    220.0, 329.63, 392.0, 523.25, 392.0, 329.63,
                    196.0, 293.66, 392.0, 493.88, 392.0, 293.66
                )
                val stepSamples = (totalSamples / notes.size).coerceAtLeast(1)
                for (i in 0 until totalSamples) {
                    val noteIdx = (i / stepSamples).coerceIn(0, notes.lastIndex)
                    val localT = (i % stepSamples).toDouble() / sampleRate
                    val f = notes[noteIdx]
                    val pluck = exp(-localT * 4.5) * (1.0 - exp(-localT * 140.0))
                    val tone = sin(2.0 * PI * f * localT) * 0.70 +
                        sin(2.0 * PI * f * 2.0 * localT) * 0.25 * exp(-localT * 6.0) +
                        sin(2.0 * PI * f * 3.0 * localT) * 0.12 * exp(-localT * 10.0)
                    out[i] = tone * pluck * 0.75
                }
            }

            "sfx_whoosh" -> {
                var lp = 0.0
                for (i in 0 until totalSamples) {
                    val frac = i.toDouble() / totalSamples
                    val env = sin(PI * frac).pow(1.4)
                    val alpha = 0.04 + 0.38 * sin(PI * frac)
                    val noise = rng.nextDouble() * 2.0 - 1.0
                    lp += alpha * (noise - lp)
                    val tone = sin(2.0 * PI * (120.0 + 360.0 * sin(PI * frac)) * (i.toDouble() / sampleRate)) * 0.22
                    out[i] = (lp * 0.95 + tone) * env * 0.9
                }
            }

            "sfx_cinema_boom" -> {
                var phase = 0.0
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = 145.0 * exp(-t * 5.2) + 36.0
                    phase += 2.0 * PI * freq / sampleRate
                    val sub = sin(phase) * exp(-t * 2.2)
                    val punch = (rng.nextDouble() * 2.0 - 1.0) * exp(-t * 28.0) * 0.55
                    val body = sin(phase * 1.5) * exp(-t * 4.5) * 0.28
                    out[i] = (sub * 0.85 + punch + body) * (1.0 - exp(-t * 200.0))
                }
            }

            "sfx_shutter" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val click1 = if (t < 0.06) {
                        ((rng.nextDouble() * 2.0 - 1.0) * 0.7 + sin(2.0 * PI * 2400.0 * t) * 0.3) * exp(-t * 75.0)
                    } else 0.0
                    val t2 = t - 0.11
                    val click2 = if (t2 in 0.0..0.09) {
                        ((rng.nextDouble() * 2.0 - 1.0) * 0.65 + sin(2.0 * PI * 1850.0 * t2) * 0.35) * exp(-t2 * 55.0)
                    } else 0.0
                    out[i] = (click1 + click2) * 0.95
                }
            }

            "sfx_glitch_zap" -> {
                var phase = 0.0
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val frac = i.toDouble() / totalSamples
                    val mod = sin(2.0 * PI * 42.0 * t) * 320.0
                    val freq = (1400.0 * (1.0 - frac * 0.72) + mod).coerceAtLeast(90.0)
                    phase += 2.0 * PI * freq / sampleRate
                    val gate = if (((t * 24.0).toInt() % 3) == 0) 0.35 else 1.0
                    val env = exp(-t * 3.4) * (1.0 - exp(-t * 180.0))
                    out[i] = sin(phase) * env * gate * 0.82
                }
            }

            "sfx_sparkle_chime" -> {
                val freqs = doubleArrayOf(1046.5, 1318.5, 1567.98, 2093.0)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    var sum = 0.0
                    freqs.forEachIndexed { idx, f ->
                        val localT = t - idx * 0.055
                        if (localT > 0.0) {
                            sum += sin(2.0 * PI * f * localT) * exp(-localT * 3.2) * (1.0 - exp(-localT * 180.0))
                        }
                    }
                    out[i] = (sum / freqs.size) * 1.4
                }
            }

            "sfx_riser" -> {
                var phase = 0.0
                var lp = 0.0
                for (i in 0 until totalSamples) {
                    val frac = i.toDouble() / totalSamples
                    val freq = 110.0 * (2.0.pow(frac * 3.4))
                    phase += 2.0 * PI * freq / sampleRate
                    val noise = rng.nextDouble() * 2.0 - 1.0
                    lp += (0.05 + 0.45 * frac) * (noise - lp)
                    val env = frac.pow(1.6) * (if (frac > 0.94) (1.0 - frac) / 0.06 else 1.0)
                    out[i] = (sin(phase) * 0.62 + sin(phase * 1.5) * 0.22 + lp * 0.45) * env * 0.88
                }
            }

            "sfx_pop" -> {
                var phase = 0.0
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = 260.0 + 620.0 * exp(-t * 28.0)
                    phase += 2.0 * PI * freq / sampleRate
                    val env = exp(-t * 16.0) * (1.0 - exp(-t * 260.0))
                    out[i] = sin(phase) * env * 0.92
                }
            }

            else -> {
                // Soft rain ambience (pink-ish filtered noise with gentle droplet accents)
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                for (i in 0 until totalSamples) {
                    val frac = i.toDouble() / totalSamples
                    val env = smoothEdgeEnvelope(frac, 0.08, 0.10)
                    val white = rng.nextDouble() * 2.0 - 1.0
                    b0 = 0.997 * b0 + white * 0.03
                    b1 = 0.985 * b1 + white * 0.05
                    b2 = 0.950 * b2 + white * 0.08
                    out[i] = (b0 + b1 + b2 + white * 0.04) * env * 0.55
                }
            }
        }

        // Apply global gentle edge fade & convert to 16-bit PCM
        val fadeSamples = (sampleRate * 0.015).toInt().coerceAtMost(totalSamples / 4)
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            var sample = out[i]
            if (i < fadeSamples) {
                sample *= i.toDouble() / fadeSamples.coerceAtLeast(1)
            } else if (i > totalSamples - fadeSamples) {
                sample *= (totalSamples - i).toDouble() / fadeSamples.coerceAtLeast(1)
            }
            val clamped = sample.coerceIn(-0.96, 0.96)
            pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    private fun smoothEdgeEnvelope(frac: Double, fadeInFrac: Double, fadeOutFrac: Double): Double {
        val inGain = if (fadeInFrac > 0.0 && frac < fadeInFrac) (frac / fadeInFrac) else 1.0
        val outGain = if (fadeOutFrac > 0.0 && frac > 1.0 - fadeOutFrac) ((1.0 - frac) / fadeOutFrac) else 1.0
        return (inGain * outGain).coerceIn(0.0, 1.0)
    }

    private fun writePcm16MonoWav(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val byteRate = sampleRate * 2
        val header = ByteArrayOutputStream(44).apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLe(36 + dataSize)
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLe(16) // PCM chunk size
            writeShortLe(1) // PCM format
            writeShortLe(1) // Mono
            writeIntLe(sampleRate)
            writeIntLe(byteRate)
            writeShortLe(2) // Block align
            writeShortLe(16) // Bits per sample
            write("data".toByteArray(Charsets.US_ASCII))
            writeIntLe(dataSize)
        }.toByteArray()

        FileOutputStream(file).use { out ->
            out.write(header)
            val pcmBytes = ByteArray(dataSize)
            var idx = 0
            for (s in samples) {
                pcmBytes[idx++] = (s.toInt() and 0xFF).toByte()
                pcmBytes[idx++] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
            out.write(pcmBytes)
        }
    }

    private fun ByteArrayOutputStream.writeIntLe(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLe(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
    }
}
