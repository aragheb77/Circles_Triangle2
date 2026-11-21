package com.thecirclestriangle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Every sound effect in the game. */
enum class Sfx { CLICK, PLACE_ONE, PLACE_TWO, LINE, TICK, STARTER, UNDO, WIN, LOSE, DRAW }

/**
 * Game audio. No audio files are bundled: all effects and the background music are synthesised
 * once at start-up (a few hundred ms on a background thread) and played with SoundPool / a looping
 * static AudioTrack. Calls made before loading has finished are silently ignored.
 *
 * Music and effects each have an on/off switch; both are remembered between launches.
 */
object Sound {
    private const val RATE = 22050
    private const val PREFS = "circles_triangle_audio"
    private const val CACHE_VERSION = 1

    /** Observable by Compose. Use [setMusic] / [setEffects] to change them (they persist the choice). */
    var musicOn by mutableStateOf(true)
        private set
    var effectsOn by mutableStateOf(true)
        private set

    private val lock = Any()
    private var appContext: Context? = null
    private var pool: SoundPool? = null
    private val soundIds = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()
    private var music: AudioTrack? = null
    private var foreground = false
    private var started = false

    fun init(context: Context) {
        synchronized(lock) {
            if (started) return
            started = true
            appContext = context.applicationContext
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        musicOn = prefs.getBoolean("music", true)
        effectsOn = prefs.getBoolean("effects", true)
        Thread({ build() }, "sound-synth").apply { isDaemon = true }.start()
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        save()
        applyMusic()
    }

    fun setEffects(on: Boolean) {
        effectsOn = on
        save()
        if (on) play(Sfx.CLICK)
    }

    /** Called from the activity: music only plays while the app is visible. */
    fun setForeground(value: Boolean) {
        synchronized(lock) { foreground = value }
        applyMusic()
    }

    fun play(sfx: Sfx) {
        if (!effectsOn) return
        val sp: SoundPool
        val id: Int
        synchronized(lock) {
            sp = pool ?: return
            id = soundIds[sfx] ?: return
            if (id !in loaded) return
        }
        val volume = when (sfx) { Sfx.CLICK, Sfx.TICK -> 0.55f; else -> 0.9f }
        sp.play(id, volume, volume, 1, 0, 1f)
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("music", musicOn)?.putBoolean("effects", effectsOn)?.apply()
    }

    private fun applyMusic() {
        synchronized(lock) {
            val track = music ?: return
            try {
                if (musicOn && foreground) {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                } else if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
            } catch (_: IllegalStateException) { /* track released by the system: stay silent */ }
        }
    }

    // ------------------------------------------------------------------ building
    private fun build() {
        val ctx = appContext ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // --- effects: write small WAV files to the cache and hand them to SoundPool
        val sp = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(lock) { loaded.add(sampleId) }
        }
        val dir = File(ctx.cacheDir, "sfx_v$CACHE_VERSION").apply { mkdirs() }
        val ids = HashMap<Sfx, Int>()
        for (sfx in Sfx.values()) {
            val file = File(dir, sfx.name.lowercase() + ".wav")
            try {
                if (!file.exists() || file.length() == 0L) writeWav(file, Synth.effect(sfx))
                ids[sfx] = sp.load(file.absolutePath, 1)
            } catch (_: Exception) { /* a missing effect must never crash the game */ }
        }
        synchronized(lock) { pool = sp; soundIds.putAll(ids) }

        // --- background music: one seamless static loop
        try {
            val pcm = Synth.music()
            val bytes = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.setVolume(0.38f)
            synchronized(lock) { music = track }
            applyMusic()
        } catch (_: Exception) { /* no music is better than a crash */ }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) data.putShort(s)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.capacity())
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
    }

    // ------------------------------------------------------------------ synthesis
    private object Synth {
        /** Note frequency from a MIDI number. */
        fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        /**
         * Soft bell / pluck: sine with two quiet harmonics, short attack, exponential decay.
         * [glide] > 1 starts the pitch higher and settles on [freq] within ~40 ms (used for "pop" sounds).
         */
        fun bell(buf: FloatArray, startSec: Double, freq: Double, lenSec: Double, vol: Double,
                 decay: Double = 6.0, glide: Double = 1.0) {
            val start = (startSec * RATE).toInt()
            val n = (lenSec * RATE).toInt()
            var phase = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx !in buf.indices) break
                val t = i.toDouble() / RATE
                val f = freq * (1.0 + (glide - 1.0) * exp(-t * 60.0))
                phase += 2.0 * PI * f / RATE
                val tone = sin(phase) + 0.30 * sin(2 * phase) + 0.10 * sin(3 * phase)
                val attack = min(1.0, t / 0.004)
                val release = min(1.0, (lenSec - t) / 0.02)
                buf[idx] += (tone * vol * attack * release * exp(-t * decay)).toFloat()
            }
        }

        /** Slow pad voice (used by the music): sine with soft fade in/out, wrapping around the loop end. */
        fun pad(buf: FloatArray, startSec: Double, freq: Double, lenSec: Double, vol: Double) {
            val start = (startSec * RATE).toInt()
            val n = (lenSec * RATE).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = min(min(1.0, t / 0.5), (lenSec - t) / 0.7).coerceAtLeast(0.0)
                val v = (sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * freq * 2.0 * t)) * vol * env
                buf[(start + i) % buf.size] += v.toFloat()
            }
        }

        /** Like [bell] but the tail wraps to the start of the buffer so the music loops without a gap. */
        fun wrapBell(buf: FloatArray, startSec: Double, freq: Double, lenSec: Double, vol: Double, decay: Double) {
            val start = (startSec * RATE).toInt()
            val n = (lenSec * RATE).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val tone = sin(2 * PI * freq * t) + 0.22 * sin(2 * PI * freq * 2.0 * t)
                val env = min(1.0, t / 0.006) * min(1.0, (lenSec - t) / 0.05) * exp(-t * decay)
                buf[(start + i) % buf.size] += (tone * vol * env).toFloat()
            }
        }

        fun toPcm(buf: FloatArray, peak: Double): ShortArray {
            var max = 1e-9f
            for (v in buf) if (kotlin.math.abs(v) > max) max = kotlin.math.abs(v)
            val g = peak / max
            return ShortArray(buf.size) { (buf[it] * g * 32767).toInt().coerceIn(-32767, 32767).toShort() }
        }

        fun effect(sfx: Sfx): ShortArray {
            // MIDI: C5 = 72, E5 = 76, G5 = 79, C6 = 84
            return when (sfx) {
                Sfx.CLICK -> FloatArray((0.09 * RATE).toInt()).also { bell(it, 0.0, 880.0, 0.08, 1.0, decay = 40.0) }.let { toPcm(it, 0.45) }
                Sfx.TICK -> FloatArray((0.07 * RATE).toInt()).also { bell(it, 0.0, 1320.0, 0.06, 1.0, decay = 60.0) }.let { toPcm(it, 0.5) }
                // a wooden "pop" - Player 1 slightly higher than Player 2 so the two can be told apart by ear
                Sfx.PLACE_ONE -> FloatArray((0.3 * RATE).toInt()).also { bell(it, 0.0, hz(76), 0.28, 1.0, decay = 14.0, glide = 1.6) }.let { toPcm(it, 0.8) }
                Sfx.PLACE_TWO -> FloatArray((0.3 * RATE).toInt()).also { bell(it, 0.0, hz(69), 0.28, 1.0, decay = 14.0, glide = 1.6) }.let { toPcm(it, 0.8) }
                // scoring: bright rising arpeggio
                Sfx.LINE -> FloatArray((0.9 * RATE).toInt()).also { b ->
                    listOf(72, 76, 79, 84).forEachIndexed { i, m -> bell(b, i * 0.075, hz(m), 0.6, 0.7, decay = 5.5) }
                }.let { toPcm(it, 0.8) }
                Sfx.STARTER -> FloatArray((1.0 * RATE).toInt()).also { b ->
                    bell(b, 0.0, hz(79), 0.7, 0.8, decay = 4.5); bell(b, 0.11, hz(84), 0.8, 0.8, decay = 4.0); bell(b, 0.11, hz(88), 0.8, 0.35, decay = 4.0)
                }.let { toPcm(it, 0.8) }
                Sfx.UNDO -> FloatArray((0.25 * RATE).toInt()).also { b ->
                    var ph = 0.0
                    for (i in b.indices) {
                        val t = i.toDouble() / RATE
                        ph += 2 * PI * (700.0 - 1500.0 * t) / RATE
                        b[i] = (sin(ph) * exp(-t * 11.0) * min(1.0, t / 0.005)).toFloat()
                    }
                }.let { toPcm(it, 0.5) }
                Sfx.WIN -> FloatArray((2.2 * RATE).toInt()).also { b ->
                    val notes = listOf(72, 76, 79, 84)
                    notes.forEachIndexed { i, m -> bell(b, i * 0.13, hz(m), 0.9, 0.7, decay = 3.5) }
                    for (m in listOf(79, 84, 88)) bell(b, 0.62, hz(m), 1.5, 0.55, decay = 2.2)
                    bell(b, 0.62, hz(72), 1.5, 0.5, decay = 2.2)
                }.let { toPcm(it, 0.85) }
                Sfx.LOSE -> FloatArray((1.8 * RATE).toInt()).also { b ->
                    listOf(69, 65, 62).forEachIndexed { i, m -> bell(b, i * 0.28, hz(m), 1.0, 0.7, decay = 3.5) }
                    bell(b, 0.84, hz(57), 0.9, 0.5, decay = 3.0)
                }.let { toPcm(it, 0.75) }
                Sfx.DRAW -> FloatArray((1.5 * RATE).toInt()).also { b ->
                    bell(b, 0.0, hz(72), 0.9, 0.7, decay = 4.0); bell(b, 0.22, hz(67), 1.0, 0.7, decay = 3.5)
                }.let { toPcm(it, 0.7) }
            }
        }

        /**
         * A calm, looping ~20 s track: slow pad chords, a soft bass note and a gently arpeggiated
         * pluck, C - Am - F - G - C - Am - Dm - G at 96 BPM.
         */
        fun music(): ShortArray {
            val eighth = 60.0 / 96.0 / 2.0          // seconds
            val chordLen = 8 * eighth                // 4 beats per chord
            // bass root (MIDI) + chord tones in semitones above the root; everything stays in C major
            val chords = listOf(
                48 to intArrayOf(0, 7, 12, 16), 45 to intArrayOf(0, 7, 12, 15), 41 to intArrayOf(0, 7, 12, 16), 43 to intArrayOf(0, 7, 12, 16),
                48 to intArrayOf(0, 7, 12, 16), 45 to intArrayOf(0, 7, 12, 15), 50 to intArrayOf(0, 7, 12, 15), 43 to intArrayOf(0, 7, 12, 16),
            )
            val total = chords.size * chordLen
            val buf = FloatArray((total * RATE).toInt())
            val arp = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)
            // sparse melody: (eighth-step inside the chord, semitones above the chord root + 12)
            val melody = listOf(
                listOf(0 to 16, 4 to 19), listOf(2 to 15, 6 to 12), listOf(0 to 16, 3 to 14, 6 to 12), listOf(4 to 16, 6 to 14),
                listOf(0 to 19, 3 to 16), listOf(2 to 15, 5 to 19, 7 to 15), listOf(0 to 17, 4 to 15, 6 to 12), listOf(2 to 14, 4 to 16, 6 to 19),
            )
            chords.forEachIndexed { ci, (root, tones) ->
                val t0 = ci * chordLen
                for (k in 0..2) pad(buf, t0, hz(root + 12 + tones[k]), chordLen + 0.6, 0.055)
                wrapBell(buf, t0, hz(root), chordLen * 0.9, 0.22, decay = 1.6)         // bass
                for (step in 0 until 8) {
                    val m = root + 12 + tones[arp[step]]
                    wrapBell(buf, t0 + step * eighth, hz(m), 0.7, 0.10, decay = 5.0)   // arpeggio
                }
                for ((step, semi) in melody[ci]) {
                    wrapBell(buf, t0 + step * eighth, hz(root + 12 + semi), 1.1, 0.16, decay = 3.2)  // melody
                }
            }
            return toPcm(buf, 0.8)
        }
    }
}
